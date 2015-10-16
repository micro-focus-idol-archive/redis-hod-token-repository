/*
 * Copyright 2015 Hewlett-Packard Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */

package com.hp.autonomy.hod.redis;

import com.hp.autonomy.hod.client.api.authentication.AuthenticationToken;
import com.hp.autonomy.hod.client.token.TokenProxy;
import org.joda.time.DateTime;
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
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;

public class RedisTokenRepositoryITCase {

    private static final long THE_PAST = 1234567890L;
    private static Pool<Jedis> pool;

    private RedisTokenRepository tokenRepository;

    @BeforeClass
    public static void setUpAll() {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(1);

        pool = new JedisPool(
            poolConfig,
            System.getProperty("hp.hod.redisHost", "localhost"),
            Integer.parseInt(System.getProperty("hp.hod.redisPort", "6379")),
            Protocol.DEFAULT_TIMEOUT,
            null,
            Integer.parseInt(System.getProperty("hp.hod.redisDb", "0"))
        );
    }

    @Before
    public void setUp() {
        tokenRepository = new RedisTokenRepository(new RedisTokenRepositoryConfig.Builder()
            .setHost(System.getProperty("hp.hod.redisHost", "localhost"))
            .setPort(Integer.parseInt(System.getProperty("hp.hod.redisPort", "6379")))
            .setDatabase(Integer.parseInt(System.getProperty("hp.hod.redisDb", "0")))
            .build());
    }

    @After
    public void tearDown() {
        try(final Jedis jedis = pool.getResource()) {
            jedis.flushDB();
        }

        tokenRepository.destroy();
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
        final AuthenticationToken token = new AuthenticationToken(getExpiry(), "foo", "bar", "baz", getRefresh());

        final TokenProxy key = tokenRepository.insert(token);

        assertThat(tokenRepository.get(key), is(token));
    }

    @Test
    public void testUpdate() throws IOException {
        final AuthenticationToken token = new AuthenticationToken(getExpiry(), "foo", "bar", "baz", getRefresh());

        final TokenProxy key = tokenRepository.insert(token);

        final AuthenticationToken newToken = new AuthenticationToken(getExpiry() + 3600L, "this", "token", "differs", getRefresh() + 3600L);
        tokenRepository.update(key, newToken);

        assertThat(tokenRepository.get(key), is(newToken));
    }

    @Test
    public void testUpdateReturnsNullAndDoesNothingIfKeyNotPresent() throws IOException {
        final TokenProxy key = new TokenProxy();
        final AuthenticationToken oldToken = tokenRepository.update(key, new AuthenticationToken(getExpiry(), "foo", "bar", "baz", getRefresh()));

        assertThat(oldToken, is(nullValue()));
        assertThat(tokenRepository.get(key), is(nullValue()));
    }

    @Test
    public void testRemove() throws IOException {
        final AuthenticationToken token = new AuthenticationToken(getExpiry(), "foo", "bar", "baz", getRefresh());
        final TokenProxy key = tokenRepository.insert(token);

        assertThat(tokenRepository.remove(key), is(token));
        assertThat(tokenRepository.get(key), is(nullValue()));
    }

    @Test
    public void testExpiryOnInsert() throws IOException, InterruptedException {
        final long expiry1 = DateTime.now().plusSeconds(2).getMillis() / 1000L;
        final long expiry2 = DateTime.now().plusSeconds(12).getMillis() / 1000L;

        final AuthenticationToken token1 = new AuthenticationToken(expiry1, "foo", "bar", "baz", getRefresh());
        final TokenProxy key1 = tokenRepository.insert(token1);

        final AuthenticationToken token2 = new AuthenticationToken(expiry2, "foo", "bar", "baz", getRefresh());
        final TokenProxy key2 = tokenRepository.insert(token2);

        TimeUnit.SECONDS.sleep(3L);

        assertThat(tokenRepository.get(key1), is(nullValue()));
        assertThat(tokenRepository.get(key2), is(token2));
    }

    @Test
    public void testExpiryOnUpdate() throws IOException, InterruptedException {
        final long expiry1 = DateTime.now().plusSeconds(2).getMillis() / 1000L;
        final long expiry2 = DateTime.now().plusSeconds(5).getMillis() / 1000L;
        final AuthenticationToken token1 = new AuthenticationToken(expiry1, "foo", "bar", "baz", getRefresh());

        final TokenProxy key = tokenRepository.insert(token1);

        final AuthenticationToken token2 = new AuthenticationToken(expiry2, "foo", "bar", "baz", getRefresh());
        tokenRepository.update(key, token2);

        TimeUnit.SECONDS.sleep(3L);
        assertThat(tokenRepository.get(key), is(token2));

        TimeUnit.SECONDS.sleep(3L);
        assertThat(tokenRepository.get(key), is(nullValue()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInsertThrowsIllegalArgumentExceptionForExpiredTokens() throws IOException {
        final AuthenticationToken token = new AuthenticationToken(THE_PAST, "foo", "bar", "baz", THE_PAST);

        tokenRepository.insert(token);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUpdateThrowsIllegalArgumentExceptionForExpiredTokens() throws IOException {
        final AuthenticationToken token1 = new AuthenticationToken(getExpiry(), "foo", "bar", "baz", getRefresh());
        final AuthenticationToken token2 = new AuthenticationToken(THE_PAST, "foo", "bar", "baz", THE_PAST);

        TokenProxy key = null;

        try {
            key = tokenRepository.insert(token1);
        } catch (final IllegalArgumentException e) {
            fail("The initial token should not have expired");
        }

        tokenRepository.update(key, token2);
    }

    private long getExpiry() {
        return DateTime.now().plusHours(1).getMillis() / 1000L;
    }

    private long getRefresh() {
        return DateTime.now().plusMinutes(30).getMillis() / 1000L;
    }
}