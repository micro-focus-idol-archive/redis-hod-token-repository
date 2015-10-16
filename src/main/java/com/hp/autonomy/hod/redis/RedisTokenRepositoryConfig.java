/*
 * Copyright 2015 Hewlett-Packard Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */

package com.hp.autonomy.hod.redis;

import lombok.Data;
import lombok.Setter;
import lombok.experimental.Accessors;
import redis.clients.jedis.Protocol;

/**
 * Configuration settings for a single Redis instance.  Use {@link com.hp.autonomy.hod.redis.RedisTokenRepositoryConfig.Builder}
 * to construct new instances.
 */
@Data
public class RedisTokenRepositoryConfig {

    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final int timeout;

    @Setter
    @Accessors(chain = true)
    public static class Builder {

        /**
         * @param host The host of the Redis. This property is required.
         */
        private String host;

        /**
         * @param port The port of the Redis. This property is required.
         */
        private int port;

        /**
         * @param password The password of the Redis. This may be omitted if no password is used.
         */
        private String password;

        /**
         * @param database The Redis database to use. Defaults to 0.
         */
        private int database = 0;

        /**
         * @param timeout The timeout on the Redis. Defaults to 2000ms.
         */
        private int timeout = Protocol.DEFAULT_TIMEOUT;

        public RedisTokenRepositoryConfig build() {
            return new RedisTokenRepositoryConfig(host, port, password, database, timeout);
        }
    }

}
