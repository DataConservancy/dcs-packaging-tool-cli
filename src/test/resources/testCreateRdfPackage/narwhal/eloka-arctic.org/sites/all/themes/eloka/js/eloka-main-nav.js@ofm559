/**
* hoverIntent r6 // 2011.02.26 // jQuery 1.5.1+
* <http://cherne.net/brian/resources/jquery.hoverIntent.html>
* 
* @param  f  onMouseOver function || An object with configuration options
* @param  g  onMouseOut function  || Nothing (use configuration options object)
* @author    Brian Cherne brian(at)cherne(dot)net
*/
(function($){$.fn.hoverIntent=function(f,g){var cfg={sensitivity:7,interval:75,timeout:0};cfg=$.extend(cfg,g?{over:f,out:g}:f);var cX,cY,pX,pY;var track=function(ev){cX=ev.pageX;cY=ev.pageY};var compare=function(ev,ob){ob.hoverIntent_t=clearTimeout(ob.hoverIntent_t);if((Math.abs(pX-cX)+Math.abs(pY-cY))<cfg.sensitivity){$(ob).unbind("mousemove",track);ob.hoverIntent_s=1;return cfg.over.apply(ob,[ev])}else{pX=cX;pY=cY;ob.hoverIntent_t=setTimeout(function(){compare(ev,ob)},cfg.interval)}};var delay=function(ev,ob){ob.hoverIntent_t=clearTimeout(ob.hoverIntent_t);ob.hoverIntent_s=0;return cfg.out.apply(ob,[ev])};var handleHover=function(e){var ev=jQuery.extend({},e);var ob=this;if(ob.hoverIntent_t){ob.hoverIntent_t=clearTimeout(ob.hoverIntent_t)}if(e.type=="mouseenter"){pX=ev.pageX;pY=ev.pageY;$(ob).bind("mousemove",track);if(ob.hoverIntent_s!=1){ob.hoverIntent_t=setTimeout(function(){compare(ev,ob)},cfg.interval)}}else{$(ob).unbind("mousemove",track);if(ob.hoverIntent_s==1){ob.hoverIntent_t=setTimeout(function(){delay(ev,ob)},cfg.timeout)}}};return this.bind('mouseenter',handleHover).bind('mouseleave',handleHover)}})(jQuery);

/* script below by Teri Hoyer, edit the arrays for each menu to highlight root-level folders that do not match links in the main menu */

jQuery(document).ready(function($) { 


    var pathname = location.pathname;
    var highlight;
    if(pathname == "/") 
        highlight = $('#navigation ul.menu > li:first > a:first');
    else {
        var path = pathname.split('/')[1];
        if (path)
            highlight = $('#navigation ul.menu li a[href$="' + path + '"]');
    }
	
	
    highlight.parent('li').addClass('active-nav');

		
		$('#navigation ul.menu li.expanded').hoverIntent(function() {			
			$(this).stop().animate({ backgroundColor: "#0095cc"}, 600);
			$(this).addClass('open');
			$('ul', this)
				.stop(true, true)
				.slideDown(1200, 'easeOutBack');
		}, function() {
			$(this).stop().animate({ backgroundColor: "#918682"}, 600);
			$(this).removeClass("open")
			$('ul', this)				
				.stop(true, true)
				.slideUp(100);
		});
      

});