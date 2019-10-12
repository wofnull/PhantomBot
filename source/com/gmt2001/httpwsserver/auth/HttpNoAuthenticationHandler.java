/*
 * Copyright (C) 2016-2018 phantombot.tv
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
package com.gmt2001.httpwsserver.auth;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

/**
 * Provides a {@link HttpAuthenticationHandler} that allows all requests
 *
 * @author gmt2001
 */
public class HttpNoAuthenticationHandler implements HttpAuthenticationHandler {

    private static HttpNoAuthenticationHandler INSTANCE;

    public static synchronized HttpNoAuthenticationHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new HttpNoAuthenticationHandler();
        }

        return INSTANCE;
    }

    private HttpNoAuthenticationHandler() {
    }

    @Override
    public boolean checkAuthorization(ChannelHandlerContext ctx, FullHttpRequest req) {
        return true;
    }
}
