$("div").hover(function(event){ 
	        $.ajax({
			    url: 'http://localhost:9090/analytics',
			    type: 'POST',
		    	    //dataType: 'json', 
		    	    dataType: 'text/plain', 
			    data:JSON.stringify({"event":"hover","hoverOn":event.target.nodeName})
		});
	    }, function(){ console.log("hover is over!");
});
$("div").click(function(event){ 
		console.log("click!")
	        $.ajax({
			    url: 'http://localhost:9090/analytics',
			    type: 'POST',
		    	    //dataType: 'json',
		    	    dataType: 'text/plain', 
			    data:JSON.stringify({"event":"click","clickOn":event.target.nodeName})
		});
	    });
