/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
(function ($) {
    
    $(document).ready(function() {

        var show_all = $("p[id$='p_rules_show_all']");
        var show_applicable = $("p[id$='p_rules_show_applicable']");

        show_all.find("a").click(function() {
            show_all.addClass('hidden');
            show_applicable.removeClass('hidden');
            $(".not-applicable-rule").removeClass('hidden');

            return false;
        });

        show_applicable.find("a").click(function() {
            show_applicable.addClass('hidden');
            show_all.removeClass('hidden');
            $(".not-applicable-rule").addClass('hidden');

            return false;
        });

        $('.tooltip').click(function(){
            return false;
        });

        var show = $("p[id$='p_rules_show']");
        var hide= $("p[id$='p_rules_hide']");

        show.find("a").click(function() {
            show.addClass('hidden');
            hide.removeClass('hidden');
            $(".item-not-applicable ul").removeClass('hidden');

            return false;
        });

        hide.find("a").click(function() {
            hide.addClass('hidden');
            show.removeClass('hidden');
            $(".item-not-applicable ul").addClass('hidden');

            return false;
        });

    });


})(jQuery);
