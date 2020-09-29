/*
 * Copyright (C) 2016-2020 phantom.bot
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.gmt2001;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.QueryStringEncoder;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Calendar;
import java.util.Collections;
import java.util.Enumeration;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;
import tv.phantombot.CaselessProperties;
import tv.phantombot.PhantomBot;

/**
 *
 * @author gmt2001
 */
public class TwitchAuthorizationCodeFlow {

    private static final String BASE_URL = "https://id.twitch.tv/oauth2";
    private static final String USER_AGENT = "PhantomBot/2020";
    private Timer t = null;

    public TwitchAuthorizationCodeFlow() {
        startup();
    }

    private void refreshTokens() {
        boolean changed = false;
        if (PhantomBot.instance().getProperties().getProperty("refresh") != null && !PhantomBot.instance().getProperties().getProperty("refresh").isBlank()) {
            JSONObject result = tryRefresh(PhantomBot.instance().getProperties().getProperty("refresh"));

            if (result.has("error")) {
                com.gmt2001.Console.err.println(result.toString());
            } else {
                PhantomBot.instance().getProperties().setProperty("oauth", result.getString("access_token"));
                PhantomBot.instance().getProperties().setProperty("refresh", result.getString("refresh_token"));

                com.gmt2001.Console.out.println("Refreshed the bot token");
                changed = true;
            }
        }

        if (PhantomBot.instance().getProperties().getProperty("apirefresh") != null && !PhantomBot.instance().getProperties().getProperty("apirefresh").isBlank()) {
            JSONObject result = tryRefresh(PhantomBot.instance().getProperties().getProperty("apirefresh"));

            if (result.has("error")) {
                com.gmt2001.Console.err.println(result.toString());
            } else {
                PhantomBot.instance().getProperties().setProperty("apioauth", result.getString("access_token"));
                PhantomBot.instance().getProperties().setProperty("apirefresh", result.getString("refresh_token"));

                com.gmt2001.Console.out.println("Refreshed the broadcaster token");
                changed = true;
            }
        }

        if (changed) {
            CaselessProperties outputProperties = new CaselessProperties() {
                @Override
                public synchronized Enumeration<Object> keys() {
                    return Collections.enumeration(new TreeSet<>(super.keySet()));
                }
            };

            try {
                try (FileOutputStream outputStream = new FileOutputStream("./config/botlogin.txt")) {
                    outputProperties.putAll(PhantomBot.instance().getProperties());
                    outputProperties.store(outputStream, "PhantomBot Configuration File");
                }

                PhantomBot.instance().reloadProperties();
            } catch (IOException ex) {
                com.gmt2001.Console.err.printStackTrace(ex);
            }
        }
    }

    private void startup() {
        if (t != null) {
            return;
        }
        if (PhantomBot.instance().getProperties().getProperty("clientid") != null && !PhantomBot.instance().getProperties().getProperty("clientid").isBlank()
                && PhantomBot.instance().getProperties().getProperty("clientsecret") != null && !PhantomBot.instance().getProperties().getProperty("clientsecret").isBlank()) {
            t = new Timer();
            t.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    refreshTokens();
                }
            }, 0, 900);
        }
    }

    public static byte[] handleRequest(FullHttpRequest req, byte[] data) {
        if (req.uri().startsWith("/oauth/checkidsecret") && req.method() == HttpMethod.GET) {
            if (PhantomBot.instance().getProperties().getProperty("clientid") != null && !PhantomBot.instance().getProperties().getProperty("clientid").isBlank()
                    && PhantomBot.instance().getProperties().getProperty("clientsecret") != null && !PhantomBot.instance().getProperties().getProperty("clientsecret").isBlank()) {
                data = PhantomBot.instance().getProperties().getProperty("clientid").getBytes();
            } else {
                data = "false".getBytes();
            }
        } else if (req.uri().startsWith("/oauth/saveidsecret") && req.method() == HttpMethod.PUT) {
            QueryStringDecoder qsd = new QueryStringDecoder(req.content().toString(), false);
            if (!qsd.parameters().containsKey("clientid") || !qsd.parameters().containsKey("clientsecret") || qsd.parameters().get("clientid").get(0).isBlank()
                    || qsd.parameters().get("clientsecret").get(0).isBlank()) {
                data = "false".getBytes();
            } else {
                PhantomBot.instance().getProperties().setProperty("clientid", qsd.parameters().get("clientid").get(0));
                PhantomBot.instance().getProperties().setProperty("clientsecret", qsd.parameters().get("clientsecret").get(0));

                CaselessProperties outputProperties = new CaselessProperties() {
                    @Override
                    public synchronized Enumeration<Object> keys() {
                        return Collections.enumeration(new TreeSet<>(super.keySet()));
                    }
                };

                try {
                    try (FileOutputStream outputStream = new FileOutputStream("./config/botlogin.txt")) {
                        outputProperties.putAll(PhantomBot.instance().getProperties());
                        outputProperties.store(outputStream, "PhantomBot Configuration File");
                    }

                    data = qsd.parameters().get("clientid").get(0).getBytes();
                    PhantomBot.instance().reloadProperties();
                } catch (IOException ex) {
                    com.gmt2001.Console.err.printStackTrace(ex);
                    data = "true".getBytes();
                }
            }
        } else if (req.uri().startsWith("/oauth/authorize") && req.method() == HttpMethod.POST) {
            QueryStringDecoder qsd = new QueryStringDecoder(req.content().toString(), false);
            if (!qsd.parameters().containsKey("code") || !qsd.parameters().containsKey("type") || !qsd.parameters().containsKey("redirect_uri")
                    || qsd.parameters().get("code").get(0).isBlank() || qsd.parameters().get("redirect_uri").get(0).isBlank()
                    || (!qsd.parameters().get("type").get(0).equals("bot") && !qsd.parameters().get("type").get(0).equals("broadcaster"))
                    || PhantomBot.instance().getProperties().getProperty("clientid") == null || PhantomBot.instance().getProperties().getProperty("clientid").isBlank()
                    || PhantomBot.instance().getProperties().getProperty("clientsecret") == null || PhantomBot.instance().getProperties().getProperty("clientsecret").isBlank()) {
                data = "false|invalid input".getBytes();
            } else {
                JSONObject result = tryAuthorize(qsd.parameters().get("code").get(0), qsd.parameters().get("redirect_uri").get(0));
                if (result.has("error")) {
                    data = ("false|" + result.getString("message")).getBytes();
                    com.gmt2001.Console.err.println(result.toString());
                } else {
                    PhantomBot.instance().getProperties().setProperty((qsd.parameters().get("type").get(0).equals("bot") ? "" : "api") + "oauth", result.getString("access_token"));
                    PhantomBot.instance().getProperties().setProperty((qsd.parameters().get("type").get(0).equals("bot") ? "" : "api") + "refresh", result.getString("refresh_token"));

                    CaselessProperties outputProperties = new CaselessProperties() {
                        @Override
                        public synchronized Enumeration<Object> keys() {
                            return Collections.enumeration(new TreeSet<>(super.keySet()));
                        }
                    };

                    try {
                        try (FileOutputStream outputStream = new FileOutputStream("./config/botlogin.txt")) {
                            outputProperties.putAll(PhantomBot.instance().getProperties());
                            outputProperties.store(outputStream, "PhantomBot Configuration File");
                        }

                        data = "success".getBytes();
                        PhantomBot.instance().reloadProperties();
                    } catch (IOException ex) {
                        com.gmt2001.Console.err.printStackTrace(ex);
                        data = "true".getBytes();
                    }
                }
            }
        }
        return data;
    }

    private static JSONObject tryAuthorize(String code, String redirect_uri) {
        try {
            QueryStringEncoder qse = new QueryStringEncoder("/token");
            qse.addParam("client_id", PhantomBot.instance().getProperties().getProperty("clientid"));
            qse.addParam("client_secret", PhantomBot.instance().getProperties().getProperty("clientsecret"));
            qse.addParam("code", code);
            qse.addParam("grant_type", "authorization_code");
            qse.addParam("redirect_uri", redirect_uri);

            URL url = new URL(BASE_URL + qse.toString());

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

            connection.addRequestProperty("Content-Type", "application/json");
            connection.addRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);

            connection.connect();

            try (BufferedOutputStream stream = new BufferedOutputStream(connection.getOutputStream())) {
                stream.write("".getBytes());
                stream.flush();
            }

            if (connection.getResponseCode() == 200) {
                try (InputStream inStream = connection.getInputStream()) {
                    return new JSONObject(new BufferedReader(new InputStreamReader(inStream)).lines().collect(Collectors.joining("\n")));
                }
            } else {
                try (InputStream inStream = connection.getErrorStream()) {
                    return new JSONObject(new BufferedReader(new InputStreamReader(inStream)).lines().collect(Collectors.joining("\n")));
                }
            }
        } catch (IOException | NullPointerException | JSONException ex) {
            return new JSONObject("{\"error\": \"Internal\",\"message\":\"" + ex.toString() + "\",\"status\":0}");
        }
    }

    private static JSONObject tryRefresh(String refresh_token) {
        try {
            QueryStringEncoder qse = new QueryStringEncoder("/token");
            qse.addParam("client_id", PhantomBot.instance().getProperties().getProperty("clientid"));
            qse.addParam("client_secret", PhantomBot.instance().getProperties().getProperty("clientsecret"));
            qse.addParam("refresh_token", refresh_token);
            qse.addParam("grant_type", "refresh_token");

            URL url = new URL(BASE_URL + qse.toString());

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

            connection.addRequestProperty("Content-Type", "application/json");
            connection.addRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);

            connection.connect();

            try (BufferedOutputStream stream = new BufferedOutputStream(connection.getOutputStream())) {
                stream.write("".getBytes());
                stream.flush();
            }

            if (connection.getResponseCode() == 200) {
                try (InputStream inStream = connection.getInputStream()) {
                    return new JSONObject(new BufferedReader(new InputStreamReader(inStream)).lines().collect(Collectors.joining("\n")));
                }
            } else {
                try (InputStream inStream = connection.getErrorStream()) {
                    return new JSONObject(new BufferedReader(new InputStreamReader(inStream)).lines().collect(Collectors.joining("\n")));
                }
            }
        } catch (IOException | NullPointerException | JSONException ex) {
            return new JSONObject("{\"error\": \"Internal\",\"message\":\"" + ex.toString() + "\",\"status\":0}");
        }
    }
}
