#!/usr/bin/env node

var http = require('http');    
var requests = 0;
var completed= 0;

var eventTypes=["click","login","page_load","mouse_hover","content_seen","faqs_visited","faqs_maximized"];

var options = {
	  host: 'localhost',
	  port: 9090,
	  path: '/analytics',
	  method: 'PUT' 
};


function makeRequest(requestOptions){
  var responses=[];
  var req=http.request(requestOptions, function(res) {
    res.on('data', function(chunk){ responses.push(chunk); });
    res.on('end', function(){ console.log("requests="+requests+", completed="+(++completed)+" "); });
  });
  req.write('{"type":"'+eventTypes[Math.round((Math.random()*1000))%eventTypes.length]+'","param1":'+Math.random().toFixed(5)+',"param2":'+Math.random().toFixed(5)+'}\n');
  req.end();
}

function waitAndDo(times,milliseconds) {
	  setTimeout(function() {
		  	requests++;
		  	makeRequest(options);
		  	waitAndDo(times+1);
		  }, milliseconds);
}

waitAndDo(0,0);
