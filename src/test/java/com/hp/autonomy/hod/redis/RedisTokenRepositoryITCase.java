/*
 * Copyright 2015 Hewlett-Packard Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */

package com.hp.autonomy.hod.redis;

import com.hp.autonomy.hod.client.api.authentication.AuthenticationToken;
import com.hp.autonomy.hod.client.token.TokenProxy;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;
import redis.clients.util.Pool;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

public class RedisTokenRepositoryITCase {

    private static Pool<Jedis> pool;
    private RedisTokenRepository tokenRepository;

    @BeforeClass
    public static void setUpAll() {
        pool = new JedisPool(
            new JedisPoolConfig(),
            System.getProperty("hp.hod.redisHost", "localhost"),
            Integer.parseInt(System.getProperty("hp.hod.redisPort", "6379")),
            Protocol.DEFAULT_TIMEOUT,
            null,
            Integer.parseInt(System.getProperty("hp.hod.redisDb", "0"))
        );
    }

    @Before
    public void setUp() {
        tokenRepository = new RedisTokenRepository(pool);
    }

    @After
    public void tearDown() {
        try(final Jedis jedis = pool.getResource()) {
            jedis.flushDB();
        }
    }

    @AfterClass
    public static void tearDownAll() {
        pool.destroy();
    }

    @Test
    public void testGetReturnsNullIfNoKey() throws IOException {
        assertThat(tokenRepository.get(new TokenProxy()), is(nullValue()));
    }

    @Test
    public void testInsertedTokensCanBeRetrieved() throws IOException {
        final AuthenticationToken token = new AuthenticationToken(1234567890L, "foo", "bar", "baz", 1234567890L);

        final TokenProxy key = tokenRepository.insert(token);

        assertThat(tokenRepository.get(key), is(token));
    }

    @Test
    public void testUpdate() throws IOException {
        final AuthenticationToken token = new AuthenticationToken(1234567890L, "foo", "bar", "baz", 1234567890L);

        final TokenProxy key = tokenRepository.insert(token);

        final AuthenticationToken newToken = new AuthenticationToken(1234567890L + 3600L, "this", "token", "differs", 1234567890L + 3600L);
        tokenRepository.update(key, newToken);

        assertThat(tokenRepository.get(key), is(newToken));
    }

    @Test
    public void testUpdateReturnsNullAndDoesNothingIfKeyNotPresent() throws IOException {
        final TokenProxy key = new TokenProxy();
        final AuthenticationToken oldToken = tokenRepository.update(key, new AuthenticationToken(1234567890L, "foo", "bar", "baz", 1234567890L));

        assertThat(oldToken, is(nullValue()));
        assertThat(tokenRepository.get(key), is(nullValue()));
    }

    @Test
    public void testRemove() throws IOException {
        final AuthenticationToken token = new AuthenticationToken(1234567890L, "foo", "bar", "baz", 1234567890L);;
        final TokenProxy key = tokenRepository.insert(token);

        assertThat(tokenRepository.remove(key), is(token));
        assertThat(tokenRepository.get(key), is(nullValue()));
    }


}