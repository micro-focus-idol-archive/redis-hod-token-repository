/*
 * Copyright 2015 Hewlett-Packard Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */

package com.hp.autonomy.hod.redis;

import lombok.Data;
import lombok.Setter;
import lombok.experimental.Accessors;
import redis.clients.jedis.Protocol;

import java.util.Collection;

/**
 * Configuration settings for a set of Redis sentinels. Use {@link com.hp.autonomy.hod.redis.RedisTokenRepositorySentinelConfig.Builder}
 * to construct new instances.
 */
@Data
public class RedisTokenRepositorySentinelConfig {

    private final Collection<HostAndPort> hostsAndPorts;
    private final String masterName;
    private final String password;
    private final int database;
    private final Integer timeout;

    @Setter
    @Accessors(chain = true)
    public static class Builder {
        /**
         * @param hostsAndPorts A collection of hosts and ports of each Redis sentinel.
         */
        private Collection<HostAndPort> hostsAndPorts;

        /**
         * @param masterName The name of the master Redis
         */
        private String masterName;

        /**
         * @param password The password of the Redis. This may be omitted if no password is used.
         */
        private String password;

        /**
         * @param database The Redis database to use. Defaults to 0
         */
        private int database = 0;

        /**
         * @param timeout The timeout on the Redis. Defaults to 2000ms
         */
        private int timeout = Protocol.DEFAULT_TIMEOUT;

        public RedisTokenRepositorySentinelConfig build() {
            return new RedisTokenRepositorySentinelConfig(hostsAndPorts, masterName, password, database, timeout);
        }
    }

    @Data
    public static class HostAndPort {
        private final String host;
        private final int port;
    }

}
