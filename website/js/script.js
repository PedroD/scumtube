$(document).ready(function(){
  $(".smartphone-screen").slick({
  	autoplay: true,
  	fade: true,
  	dots: true
  });
});

function download() {
	$(".download-wrapper").hide();
	$("#about").hide();
	$(".disclaimer").css("display", "block");
}