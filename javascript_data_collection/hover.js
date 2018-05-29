$("div").hover(function(event){ 
		console.log("hover starts!");
	        $.ajax({
			    url: 'http://localhost:9090/analytics',
			    type: 'POST',
		    	    dataType: 'json', // Set datatype - affects Accept header
			    data:{"hoverOn":event.target.nodeName}
		});
	    }, function(){ console.log("hover is over!");
});
