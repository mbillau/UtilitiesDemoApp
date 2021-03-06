/**
* Copyright 2015 IBM Corp.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.sample;

//import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.Random;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import javax.net.ssl.HttpsURLConnection;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
//import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;
import com.ibm.mfp.adapter.api.AdaptersAPI;
import com.worklight.core.auth.OAuthSecurity;
import com.cloudant.client.org.lightcouch.NoDocumentException;

import com.cloudant.client.api.Database;
import com.cloudant.client.api.views.Key;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
//import okhttp3.RequestBody;
//import okhttp3.ResponseBody;

@Path("/")
@OAuthSecurity(enabled = false)
public class UtilitiesResource {
	/*
	 * For more info on JAX-RS see https://jax-rs-spec.java.net/nonav/2.0-rev-a/apidocs/index.html
	 */

	@Context
	AdaptersAPI adaptersAPI;

    // Access Cloudant DB
	private Database getDB() throws Exception {
		UtilitiesApplication app = adaptersAPI.getJaxRsApplication(UtilitiesApplication.class);
		if (app.db != null) {
			return app.db;
		}
		throw new Exception("Unable to connect to Cloudant DB, check the configuration.");
	}

    // Access weather service
    private String getCredentials() {
        UtilitiesApplication app = adaptersAPI.getJaxRsApplication(UtilitiesApplication.class);
        if (!app.weatherUsername.isEmpty() && !app.weatherPassword.isEmpty()) {
            return Credentials.basic(app.weatherUsername, app.weatherPassword);
        } else {
            return "";
        }
    }

	// Override SSL Trust manager without certificate chains validation
	TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager(){
			public X509Certificate[] getAcceptedIssuers(){return null;}
			public void checkClientTrusted(X509Certificate[] certs, String authType){}
			public void checkServerTrusted(X509Certificate[] certs, String authType){}
	}};

	private void fixSSL() {
        // Initializes this context with all-trusting host verifier.
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {}
    }

	/************************************************
	 *
	 * 					WORK ORDERS
	 *
     ***********************************************/

    // Basic GET for base path
 	@GET
 	@Path("/orders")
    @Produces(MediaType.TEXT_PLAIN)
    public String post1() {
        return "Hello from resources";
    }

	// POST a work order
	@POST
    @Path("/orders")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response addOrder(WorkOrder order) throws Exception {
		if(order!=null && order.isValid()) {
            // Handle SSL issue
            fixSSL();

			// Add date stamp
			order.setCreated(new Date());
			// Random int 0 to 1,000,000,000
			int id = new Random().nextInt(1000000000);
			order.set_id(Integer.toString(id));

            // Set finished date
            if (order.getInspectionFinished() && order.getDetails().getDate()==null) {
                order.getDetails().setDate(new Date());
            }

			// Handle Cloudant
			String err = getDB().post(order).getError();

			if (err != null) {
				return Response.status(500).entity(err).build();
			}
			else {
				return Response.status(201).entity(order).build();
			}

		}
		else {
			return Response.status(400).entity("Invalid work order document").build();
		}
	}

	// GET a specific work order
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/orders/{id}")
	public Response getOrder(@PathParam("id") String id) throws Exception {
        // Handle SSL issue
        fixSSL();

		try {
			WorkOrder dbOrder = getDB().find(WorkOrder.class, id);
			return Response.ok(dbOrder).build();
		} catch(NoDocumentException e){
			return Response.status(404).build();
		}
	}

	// PUT a work order
	@PUT
	@Path("/orders/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateOrder(@PathParam("id") String id, WorkOrder order) throws Exception {
        // Handle SSL issue
        fixSSL();

		WorkOrder dbOrder;

		try {
			dbOrder = getDB().find(WorkOrder.class, id);
		} catch(NoDocumentException e){
			return Response.status(404).build();
		}

		if(order!=null && order.isValid()) {
            // Set finished date
            if (order.getInspectionFinished() && order.getDetails().getDate()==null) {
                order.getDetails().setDate(new Date());
            }

            // Update all the dbOrder properties
			dbOrder.setAddedBy(order.getAddedBy());
			dbOrder.setAssignedTo(order.getAssignedTo());
            dbOrder.setInspectionFinished(order.getInspectionFinished());
            dbOrder.setDetails(order.getDetails());
			dbOrder.setLocation(order.getLocation());

			String err = getDB().update(dbOrder).getError();

			if (err != null) {
				return Response.status(500).entity(err).build();
			} else {
				return Response.status(202).entity(dbOrder).build();
			}
		}
		else {
			return Response.status(400).entity("Invalid work order document").build();
		}
	}

	// DELETE a work order
	@DELETE
	@Path("/orders/{id}")
	public Response deleteOrder(@PathParam("id") String id) throws Exception {
        // Handle SSL issue
        fixSSL();

		try {
			WorkOrder order = getDB().find(WorkOrder.class, id);
			getDB().remove(order);
			return Response.ok().build();
		} catch(NoDocumentException e){
			return Response.status(404).build();
		}
	}

	/************************************************
	 *
	 * 				USER WORK ORDERS
	 *
	 ***********************************************/

    // Basic GET for base path
  	@GET
  	@Path("/users")
    @Produces(MediaType.TEXT_PLAIN)
    public String post2() {
        return "Hello from resources";
    }

	// GET a specific user's work orders
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/users")
	public Response getOrdersByName(String name) throws Exception {
		// Handle SSL issue
		fixSSL();

		try {
			List<WorkOrder> order = getDB()
				.getViewRequestBuilder("userDoc", "userIndex")
				.newRequest(Key.Type.STRING, WorkOrder.class)
				.includeDocs(true)
				.keys(name)
				.build()
				.getResponse()
				.getDocsAs(WorkOrder.class);

			return Response.ok(order).build();

		} catch(NoDocumentException e){
			return Response.status(404).build();
		}
	}

    /************************************************
	 *
	 * 				    WEATHER
	 *
	 ***********************************************/

    // Basic GET for base path
   	@GET
   	@Path("/weather")
    @Produces(MediaType.TEXT_PLAIN)
    public String post3() {
        return "Hello from resources";
    }

    // POST a zip code array to update the weather
 	@POST
 	@Produces(MediaType.APPLICATION_JSON)
    @Consumes("application/json")
 	@Path("/weather")
 	public Response getWeatherAlerts(String[] zipCodes) throws Exception {
        /*
         * Output structure
         *  - zipLevel: outer most object
         *  - alertLevel: inner array of alerts for each zip
         *  - detailLevel: inner most object for alert details
         */
        JSONObject zipLevel = new JSONObject();
		String latitude;
        String longitude;

        // Handle SSL issue
        fixSSL();

        for (int i = 0; i < zipCodes.length; i++) {
            // Get lat/long for zips
            try {
    			ZipCode dbZip = getDB().find(ZipCode.class, "zip" + zipCodes[i]);

                latitude = dbZip.getLatitude();
                longitude = dbZip.getLongitude();

    		} catch(NoDocumentException e){
                // Get the coordintes from the weather service
                JSONObject latLong = geocode(zipCodes[i]);

                // Add the zip code to Cloudant
                ZipCode newZip = new ZipCode();
                newZip.setZip(zipCodes[i]);
                newZip.setLatitude(latLong.get("latitude").toString());
                newZip.setLongitude(latLong.get("longitude").toString());

                Response cloudantRes = addZipCode(newZip);

                // Check for errors when adding the zip
                if (cloudantRes.getStatus() == 201) {
                    latitude = newZip.getLatitude();
                    longitude = newZip.getLongitude();

                } else {
                    // Assume zip is invalid
                    continue;
                }
    		}

            // Get weather alerts from lat/long
            String weatherRes = alerts(latitude, longitude);

            // Java casting magic
            JSONObject weatherJSON = JSONObject.parse(weatherRes);
            int code = Integer.parseInt(((JSONObject)weatherJSON.get("metadata")).get("status_code").toString());

            if (code == 200) {
                JSONArray allInfo = (JSONArray)weatherJSON.get("alerts");
                JSONArray alertLevel = new JSONArray();

                // Grab the data for each alert
                for (int j = 0; j < allInfo.size(); j++) {
                    JSONObject detailLevel = new JSONObject();
                    detailLevel.put("severity", ((JSONObject)allInfo.get(j)).get("severity"));
                    detailLevel.put("headline", ((JSONObject)allInfo.get(j)).get("headline_text"));

                    alertLevel.add(detailLevel);
                }

                zipLevel.put(zipCodes[i], alertLevel);

            } else {
                // No alerts for zip code
                zipLevel.put(zipCodes[i], null);

            }
        }
        return Response.ok(zipLevel).build();
 	}

    // POST a new zip code and coordinates to Cloudant
    private Response addZipCode(ZipCode zipCode) throws Exception {
		if(zipCode!=null && zipCode.isValid()) {
            // Handle SSL issue
            fixSSL();

            // Set the id
			zipCode.set_id("zip" + zipCode.getZip());

			// Handle Cloudant
			String err = getDB().post(zipCode).getError();

			if (err != null) {
				return Response.status(500).entity(err).build();
			}
			else {
				return Response.status(201).entity(zipCode).build();
			}

		}
		else {
			return Response.status(400).entity("Invalid zip code document").build();
		}
	}

    // GET latitude and longitude for a specific zip code
  	private JSONObject geocode(String zip) throws Exception {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
            .url("https://twcservice.mybluemix.net/api/weather/v3/location/point?postalKey=" + zip + "%3AUS&language=en-US")
            .get()
            .addHeader("Authorization", getCredentials())
            .build();

        okhttp3.Response response = client.newCall(request).execute();
        String body = response.body().string();
        JSONObject json = JSONObject.parse(body);

        JSONObject output = new JSONObject();
        output.put("latitude", ((JSONObject)json.get("location")).get("latitude"));
        output.put("longitude", ((JSONObject)json.get("location")).get("longitude"));
        return output;
  	}

    // GET alerts for a specific latitude and longitude
	private String alerts(String latitude, String longitude) throws Exception {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
            .url("https://twcservice.mybluemix.net/api/weather/v1/geocode/" + latitude + "/" + longitude + "/alerts.json?language=en-US")
            .get()
            .addHeader("Authorization", getCredentials())
            .build();

        okhttp3.Response response = client.newCall(request).execute();
        return response.body().string();
	}

}
