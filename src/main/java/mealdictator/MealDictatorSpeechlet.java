/**
    Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

    Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

        http://aws.amazon.com/apache2.0/

    or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package mealdictator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.OutputSpeech;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.SsmlOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.amazonaws.util.json.JSONTokener;

/**
 * This skill creates a Lambda function for handling Alexa Skill requests that:
 * <ul>
 * <li><b>Web service</b>: communicate with an external web service to get random restaurant suggestions from mealdictator.com
 * </li>
 * <li><b>slots</b>: has 1 slot for the city the user wishes to dine in,
 * </li>
 * <p>
 * - Dialog state: Handles two models, both a one-shot ask, and a
 * dialog model. If the user provides an incorrect slot in a one-shot model, it will
 * direct to the dialog model. See the examples section for sample interactions of these models.
 * </ul>
 * <p>
 * <h2>Examples</h2>
 * <p>
 * <b>One-shot model</b>
 * <p>
 * User: "Alexa, ask Meal Dictator to find me a place to eat in Cincinnati"
 * <p>
 * <b>Dialog model</b>
 * <p>
 * User: "Alexa, open Meal Diectator"
 * <p>
 * Alexa:"Welcome to Meal Dictator. I can find you a place to eat in a U.S. city. you can say something like,
 * Find me a place to eat in Cincinnati. ... Now, which city would you like to eat in?"
 * <p>
 * User: "Seattle"
 * <p>
 * <p>
 * <p>
 * Alexa: "You'll eat at, Mamnoon , the address is, 1508 Melrose Ave"
 */
//todo: truncate address in response, add card
public class MealDictatorSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(MealDictatorSpeechlet.class);

    private static final String SLOT_CITY = "City";

    private static final String GOOGLE_MAP_URL = "http://maps.googleapis.com/maps/api/geocode/json";
    private static final String MEAL_DICTATOR_URL = "http://meal-dictator.herokuapp.com/places";

    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        // any initialization logic goes here
    }

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        return getWelcomeResponse();
    }

    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        Intent intent = request.getIntent();
        String intentName = intent.getName();

        if ("OneshotMealIntent".equals(intentName)) {
            return handleOneshotRequest(intent);
        }
        else if ("DialogMealIntent".equals(intentName)) {
            //dialog code here plz
            //we will be passed slot with value, no slot, slots with no value.

            Slot citySlot = intent.getSlot(SLOT_CITY);
            if (citySlot != null && citySlot.getValue() != null) {
                return handleDialogRequest(intent);
            }
            else {
                return handleNoSlotDialogRequest(intent);
            }
        }
        else if ("AMAZON.HelpIntent".equals(intentName)) {
            return handleHelpRequest();
        }
        else if ("AMAZON.StopIntent".equals(intentName)) {
            PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
            outputSpeech.setText("Goodbye");

            return SpeechletResponse.newTellResponse(outputSpeech);
        }
        else if ("AMAZON.CancelIntent".equals(intentName)) {
            PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
            outputSpeech.setText("Goodbye");

            return SpeechletResponse.newTellResponse(outputSpeech);
        }
        else {
            throw new SpeechletException("Invalid Intent");
        }
    }

    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
    }

    private SpeechletResponse getWelcomeResponse() {
        String whichCityPrompt = "Which city would you like to eat in?";

        String speechOutput =
                "Welcome to Meal Dictator. "
                + "I can find you a place to eat. "
                + "you can say something like, "
                + "Find me a place to eat in Cincinnati. ... Now, " + whichCityPrompt;
        String repromptText =
                "For instructions on what you can say, please say help me.";


        return newAskResponse(speechOutput, repromptText);
    }

    private SpeechletResponse handleHelpRequest() {
        String repromptText = "Which city would you like to eat in?";
        String speechOutput =
                "I can find you a place to eat. "
                + "You can simply open Meal Dictator and say something like, "
                + "feed me in Cincinnati, or, "
                + "find me a restaurant in Chicago, or, "
                + "find me a place to eat in Seattle. "
                + "Or you can say exit... "
                + "Now, " + repromptText;

        return newAskResponse(speechOutput, repromptText);
    }

    /**
     * This handles the one-shot interaction, where the user utters a phrase like: 'Alexa, open Meal
     * Dictator and find me a place to eat in Cincinnati'.
     * If there is an error in a slot, this will guide the user to the dialog approach.
     */
    private SpeechletResponse handleOneshotRequest(Intent intent) {

        Slot citySlot = intent.getSlot(SLOT_CITY);
        String speechOutput = null;
        PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
        SimpleCard card = new SimpleCard();

        try {
            if (citySlot != null && citySlot.getValue() != null) {
                String cityName = citySlot.getValue();

                Restaurant restaurant = getRestaurantData(cityName);
                speechOutput = "OK, eat at, " + restaurant.name
                        + ". The address is, " + restaurant.address;
                card.setTitle("Meal Dictator");
                card.setContent("Eat at: " + restaurant.name + ". Address: " + restaurant.address);
            }
            else {

                throw new Exception("");
            }

        } catch (Exception e) {
            speechOutput = "Please try saying the city again. For example, Cincinnati";
            return newAskResponse(speechOutput, speechOutput);
        }
        outputSpeech.setText(speechOutput);
        return SpeechletResponse.newTellResponse(outputSpeech, card);
    }

    /**
     * Handle no slots, or slot(s) with no values. In the case of a dialog based skill with multiple
     * slots, when passed a slot with no value, we cannot have confidence it is is the correct slot
     * type so we rely on session state to determine the next turn in the dialog, and reprompt.
     */
    private SpeechletResponse handleNoSlotDialogRequest(Intent intent) {
        String speechOutput = "Please try saying the city again , for example, Cincinnati";
        return newAskResponse(speechOutput, speechOutput);
    }

    /**
     * Handles the dialog step where the user provides a city.
     * Currently it contains the same code as the oneshot, leaving it for now in-case
     * more dialog slots are added in the near future
     */
    private SpeechletResponse handleDialogRequest(Intent intent) {

        Slot citySlot = intent.getSlot(SLOT_CITY);
        String speechOutput = null;
        PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
        SimpleCard card = new SimpleCard();

        try {
            if (citySlot != null && citySlot.getValue() != null) {
                String cityName = citySlot.getValue();

                Restaurant restaurant = getRestaurantData(cityName);
                speechOutput = "Ok, eat at, " + restaurant.name
                        + ". The address is, " + restaurant.address;
                card.setTitle("Meal Dictator");
                card.setContent("Eat at: " + restaurant.name + ". Address: " + restaurant.address);
            } else {
                throw new Exception("");
            }
        } catch (Exception e) {
            speechOutput = "Please try saying the city again. For example, Cincinnati";
            return newAskResponse(speechOutput, speechOutput);
        }
        outputSpeech.setText(speechOutput);
        return SpeechletResponse.newTellResponse(outputSpeech, card);
    }

    /**
     * Wrapper for creating the Ask response. The OutputSpeech and {@link Reprompt} objects are
     * created from the input strings.
     *
     * @param stringOutput
     *            the output to be spoken
     * @param repromptText
     *            the reprompt for if the user doesn't reply or is misunderstood.
     * @return SpeechletResponse the speechlet response
     */
    private SpeechletResponse newAskResponse(String stringOutput, String repromptText) {
        PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
        outputSpeech.setText(stringOutput);

        PlainTextOutputSpeech repromptOutputSpeech = new PlainTextOutputSpeech();
        repromptOutputSpeech.setText(repromptText);
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(repromptOutputSpeech);

        return SpeechletResponse.newAskResponse(outputSpeech, reprompt);
    }

    /**
     * make requests to supplied url and return JSON data
     * @param url
     * @return
     */
    private JSONObject getJSONData(URL url) {

        String speechOutput = "";
        InputStreamReader inputStream = null;
        BufferedReader bufferedReader = null;
        StringBuilder builder = new StringBuilder();
        JSONObject response = null;
        try {
            String line;
            inputStream = new InputStreamReader(url.openStream(), Charset.forName("US-ASCII"));
            bufferedReader = new BufferedReader(inputStream);

            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
            }

        } catch (IOException e) {
            // reset builder to a blank string
            builder.setLength(0);
        }
        if (builder.length() == 0) {
            speechOutput =
                    "I could not find you a restaurant as this time "
                            + "Please try again later.";
        } else {
            try {

                response = new JSONObject(new JSONTokener(builder.toString()));

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return response;
    }

    /**
     * makes google api request with city to get coordinates
     * @param city
     * @return
     */
    private JSONObject makeGoogleRequest(String city) throws Exception {
        String querySring = "?address=" + URLEncoder.encode(city,"UTF-8");
        JSONObject coordinates = null;
        URL url = new URL(GOOGLE_MAP_URL + querySring);
        JSONObject googleResponseObject = getJSONData(url);
        if (googleResponseObject != null) {
            coordinates = googleResponseObject.getJSONArray("results").getJSONObject(0).getJSONObject("geometry").getJSONObject("location");
        }
        return coordinates;

    }

    /**
     * sends request to meal dictator to retrieve restaurant data
     * @param city
     */
    private JSONObject makeRestaurantRequest(String city) throws Exception {
        JSONObject mealDictatorResponseObject = null;
        JSONObject coordinates = makeGoogleRequest(city);

        String lng = coordinates.getString("lng");
        String lat = coordinates.getString("lat");
        String querySring = "?lat=" + lat + "&lon=" + lng;
        URL url = new URL(MEAL_DICTATOR_URL + querySring);
        mealDictatorResponseObject = getJSONData(url);
        return mealDictatorResponseObject;
    }

    /**
     * wrapper to retrieve restaurant data and create a new Restaruant object
     * @param city
     * @return
     */
    private Restaurant getRestaurantData(String city) throws Exception {
        Restaurant restaurant = null;
        JSONObject restaurantData = makeRestaurantRequest(city);

        if (restaurantData != null) {
            restaurant = new Restaurant(restaurantData.getString("name"), restaurantData.getString("address"));
        }
        return restaurant;
    }

    /**
     * Encapsulates restaurant data
     */
    private static class Restaurant {
        private String name;
        private String address;

        public Restaurant(String name, String address) {
            this.name = name;
            this.address = address;
        }
    }
}
