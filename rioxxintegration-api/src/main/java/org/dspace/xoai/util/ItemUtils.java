/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.xoai.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.dspace.app.util.factory.UtilServiceFactory;
import org.dspace.app.util.service.MetadataExposureService;
import org.dspace.authority.AuthorityValue;
import org.dspace.authority.FunderAuthorityValue;
import org.dspace.authority.ProjectAuthorityValue;
import org.dspace.authority.factory.AuthorityServiceFactory;
import org.dspace.authority.orcid.Orcidv2AuthorityValue;
import org.dspace.authority.service.AuthorityValueService;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataValue;
import org.dspace.content.authority.Choices;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.Utils;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.xoai.data.DSpaceItem;

import com.lyncode.xoai.dataprovider.xml.xoai.Element;
import com.lyncode.xoai.dataprovider.xml.xoai.Metadata;
import com.lyncode.xoai.util.Base64Utils;

/**
 * 
 * @author Lyncode Development Team <dspace@lyncode.com>
 */
@SuppressWarnings("deprecation")
public class ItemUtils
{
    private static final Logger log = LogManager.getLogger(ItemUtils.class);
    
	private static final String REGEX_SELECT_PRIMARY = ConfigurationManager.getProperty("oai", "retrievemetadata.primarybistream.pattern");
	
	private static final Pattern PATTERN_SELECT_PRIMARY = Pattern.compile(REGEX_SELECT_PRIMARY);
    
    private static final MetadataExposureService metadataExposureService
            = UtilServiceFactory.getInstance().getMetadataExposureService();

    private static final ItemService itemService
            = ContentServiceFactory.getInstance().getItemService();

    private static final BitstreamService bitstreamService
            = ContentServiceFactory.getInstance().getBitstreamService();
    
    private static final AuthorityValueService authorityValueService = AuthorityServiceFactory.getInstance().getAuthorityValueService();
    
    private static final AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    
    private static final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();

    private static Element getElement(List<Element> list, String name)
    {
        for (Element e : list)
            if (name.equals(e.getName()))
                return e;

        return null;
    }
    private static Element create(String name)
    {
        Element e = new Element();
        e.setName(name);
        return e;
    }

    private static Element.Field createValue(
            String name, String value)
    {
        Element.Field e = new Element.Field();
        e.setValue(value);
        e.setName(name);
        return e;
    }
    public static Metadata retrieveMetadata (Context context, Item item) {
        Metadata metadata;

        // read all metadata into Metadata Object
        metadata = new Metadata();
        List<MetadataValue> vals = itemService.getMetadata(item, Item.ANY, Item.ANY, Item.ANY, Item.ANY);
        for (MetadataValue val : vals)
        {
            MetadataField field = val.getMetadataField();
            
            // Don't expose fields that are hidden by configuration
            try {
                if (metadataExposureService.isHidden(context,
                        field.getMetadataSchema().getName(),
                        field.getElement(),
                        field.getQualifier()))
                {
                    continue;
                }
            } catch(SQLException se) {
                throw new RuntimeException(se);
            }

            Element valueElem = null;
            Element schema = getElement(metadata.getElement(), field.getMetadataSchema().getName());
            if (schema == null)
            {
                schema = create(field.getMetadataSchema().getName());
                metadata.getElement().add(schema);
            }
            valueElem = schema;

            // Has element.. with XOAI one could have only schema and value
            if (field.getElement() != null && !field.getElement().equals(""))
            {
                Element element = getElement(schema.getElement(),
                        field.getElement());
                if (element == null)
                {
                    element = create(field.getElement());
                    schema.getElement().add(element);
                }
                valueElem = element;

                // Qualified element?
                if (field.getQualifier() != null && !field.getQualifier().equals(""))
                {
                    Element qualifier = getElement(element.getElement(),
                            field.getQualifier());
                    if (qualifier == null)
                    {
                        qualifier = create(field.getQualifier());
                        element.getElement().add(qualifier);
                    }
                    valueElem = qualifier;
                }
            }

            // Language?
            if (val.getLanguage() != null && !val.getLanguage().equals(""))
            {
                Element language = getElement(valueElem.getElement(),
                        val.getLanguage());
                if (language == null)
                {
                    language = create(val.getLanguage());
                    valueElem.getElement().add(language);
                }
                valueElem = language;
            }
            else
            {
                Element language = getElement(valueElem.getElement(),
                        "none");
                if (language == null)
                {
                    language = create("none");
                    valueElem.getElement().add(language);
                }
                valueElem = language;
            }

            valueElem.getField().add(createValue("value", val.getValue()));
            if (val.getAuthority() != null) {
                valueElem.getField().add(createValue("authority", val.getAuthority()));
                if (val.getConfidence() != Choices.CF_NOVALUE)
                    valueElem.getField().add(createValue("confidence", val.getConfidence() + ""));
            }

				AuthorityValue authorityValue = authorityValueService.findByUID(context, val.getAuthority());
				if (authorityValue!=null) {
					if (authorityValue instanceof FunderAuthorityValue) {
						String id = ((FunderAuthorityValue) authorityValue).getFunderID();
						valueElem.getField().add(createValue("authorityID", "http://dx.doi.org/" + id));						
						valueElem.getField().add(createValue(val.getAuthority(), "http://dx.doi.org/" + id));						

					} else if (authorityValue instanceof Orcidv2AuthorityValue) {
						String id = ((Orcidv2AuthorityValue) authorityValue).getOrcid_id();
						valueElem.getField().add(createValue("authorityID", "http://orcid.org/"+id));
						valueElem.getField().add(createValue(val.getAuthority(), "http://orcid.org/"+id));
					}
					else if (authorityValue instanceof ProjectAuthorityValue){
						FunderAuthorityValue funderAuthorityValue = ((ProjectAuthorityValue) authorityValue).getFunderAuthorityValue();
						if(funderAuthorityValue!=null) {
							String funderAuthorityId = funderAuthorityValue.getId();
							valueElem.getField().add(createValue("funderAuthorityID", funderAuthorityId));
						}
					}
				}
        }
        // Done! Metadata has been read!
        // Now adding bitstream info
        Element bundles = create("bundles");
        metadata.getElement().add(bundles);

        List<Bundle> bs;
        try
        {
            bs = item.getBundles();
            for (Bundle b : bs)
            {
                Element bundle = create("bundle");
                bundles.getElement().add(bundle);
                bundle.getField()
                        .add(createValue("name", b.getName()));

                Element bitstreams = create("bitstreams");
                bundle.getElement().add(bitstreams);
                List<Bitstream> bits = b.getBitstreams();
                
                UUID primary = null;
                for (Bitstream bit : bits)
                {
                    // Check if current bitstream is in original bundle + 1 of the 2 following
                    // Bitstream = primary bitstream in bundle -> true
                    if ("ORIGINAL".equals(b.getName()) && (b.getPrimaryBitstream() != null && b.getPrimaryBitstream().getID().equals(bit.getID()))) {
                        primary = bit.getID();
                    }
                    
                    // No primary bitstream found in bundle-> check the regex 
                    if(primary==null) {
                    	if ("ORIGINAL".equals(b.getName()) && b.getPrimaryBitstream() == null && PATTERN_SELECT_PRIMARY.matcher(bit.getName()).matches()) {
                    		primary = bit.getID();
                    	}
                    }

                    if(primary!=null) {
                    	break;
                    }
                }
                for (Bitstream bit : bits)
                {
                    // No primary bitstream found in bundle-> only the first one gets flagged as "primary"                	
                	if(primary==null) {
                		if ("ORIGINAL".equals(b.getName()) && (b.getPrimaryBitstream() == null && bit.getID().equals(bits.get(0).getID()))) {
                			primary = bit.getID();
                		}
                	}
                    
                    Element bitstream = create("bitstream");
                    bitstreams.getElement().add(bitstream);
                    String url = "";
                    String bsName = bit.getName();
                    String sid = String.valueOf(bit.getSequenceID());
                    String baseUrl = ConfigurationManager.getProperty("oai",
                            "bitstream.baseUrl");
                    String handle = null;
                    // get handle of parent Item of this bitstream, if there
                    // is one:
                    List<Bundle> bn = bit.getBundles();
                    if (!bn.isEmpty())
                    {
                        List<Item> bi = bn.get(0).getItems();
                        if (!bi.isEmpty())
                        {
                            handle = bi.get(0).getHandle();
                        }
                    }
                    if (bsName == null)
                    {
                        List<String> ext = bit.getFormat(context).getExtensions();
                        bsName = "bitstream_" + sid
                                + (ext.isEmpty() ? "" : ext.get(0));
                    }
                    if (handle != null && baseUrl != null)
                    {
                        url = baseUrl + "/bitstream/"
                                + handle + "/"
                                + sid + "/"
                                + URLUtils.encode(bsName);
                    }
                    else
                    {
                        url = URLUtils.encode(bsName);
                    }

                    String cks = bit.getChecksum();
                    String cka = bit.getChecksumAlgorithm();
                    String oname = bit.getSource();
                    String name = bit.getName();
                    String description = bit.getDescription();

                    addEmbargoField(context, bit, bitstream);
                    
                    if (name != null)
                        bitstream.getField().add(
                                createValue("name", name));
                    if (oname != null)
                        bitstream.getField().add(
                                createValue("originalName", name));
                    if (description != null)
                        bitstream.getField().add(
                                createValue("description", description));
                    bitstream.getField().add(
                            createValue("format", bit.getFormat(context)
                                    .getMIMEType()));
                    bitstream.getField().add(
                            createValue("size", "" + bit.getSizeBytes()));
                    bitstream.getField().add(createValue("url", url));
                    bitstream.getField().add(
                            createValue("checksum", cks));
                    bitstream.getField().add(
                            createValue("checksumAlgorithm", cka));
                    bitstream.getField().add(
                            createValue("sid", bit.getSequenceID()
                                    + ""));
						bitstream.getField().add(
								createValue("primary", ((primary!=null && primary.equals(bit.getID()))?true:false)
										+ ""));
                }
            }
        }
        catch (SQLException e1)
        {
            e1.printStackTrace();
        }
        

        // Other info
        Element other = create("others");

        other.getField().add(
                createValue("handle", item.getHandle()));
        other.getField().add(
                createValue("identifier", DSpaceItem.buildIdentifier(item.getHandle())));
        other.getField().add(
                createValue("lastModifyDate", item
                        .getLastModified().toString()));
        metadata.getElement().add(other);

        // Repository Info
        Element repository = create("repository");
        repository.getField().add(
                createValue("name",
                        ConfigurationManager.getProperty("dspace.name")));
        repository.getField().add(
                createValue("mail",
                        ConfigurationManager.getProperty("mail.admin")));
        metadata.getElement().add(repository);

        // Licensing info
        Element license = create("license");
        List<Bundle> licBundles;
        try
        {
            licBundles = itemService.getBundles(item, Constants.LICENSE_BUNDLE_NAME);
            if (!licBundles.isEmpty())
            {
                Bundle licBundle = licBundles.get(0);
                List<Bitstream> licBits = licBundle.getBitstreams();
                if (!licBits.isEmpty())
                {
                    Bitstream licBit = licBits.get(0);
                    InputStream in;
                    try
                    {
                        in = bitstreamService.retrieve(context, licBit);
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        Utils.bufferedCopy(in, out);
                        license.getField().add(
                                createValue("bin",
                                        Base64Utils.encode(out.toString())));
                        metadata.getElement().add(license);
                    }
                    catch (AuthorizeException | IOException | SQLException e)
                    {
                        log.warn(e.getMessage(), e);
                    }

                }
            }
        }
        catch (SQLException e1)
        {
            log.warn(e1.getMessage(), e1);
        }
        
        return metadata;
    }
    
	private static void addEmbargoField(Context context, Bitstream bit, Element bitstream) throws SQLException {

		List<ResourcePolicy> policies = authorizeService.getPoliciesActionFilter(context, bit, Constants.READ);
		Group group = groupService.findByName(context, Group.ANONYMOUS);

		for (ResourcePolicy policy : policies) {
			if(policy.getGroup()!=null) {
				if (group.equals(policy.getGroup())) {
					Date startDate = policies.get(0).getStartDate();
	
					if (startDate != null && startDate.after(new Date())) {
						SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
						bitstream.getField().add(
								createValue("embargo", formatter.format(startDate)));
					}
				}
			}
		}
	}
}
