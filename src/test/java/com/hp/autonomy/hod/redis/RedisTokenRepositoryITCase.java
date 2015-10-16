/*
 * Copyright 2015 Hewlett-Packard Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */

package com.hp.autonomy.hod.redis;

import com.hp.autonomy.hod.client.api.authentication.AuthenticationToken;
import com.hp.autonomy.hod.client.api.authentication.EntityType;
import com.hp.autonomy.hod.client.api.authentication.TokenType;
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

    private static final DateTime THE_PAST = new DateTime(1234567890L);
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
        assertThat(tokenRepository.get(new TokenProxy<>(EntityType.Application.INSTANCE, TokenType.Simple.INSTANCE)), is(nullValue()));
    }

    @Test
    public void testInsertedTokensCanBeRetrieved() throws IOException {
        final AuthenticationToken<EntityType.Application, TokenType.Simple> token = getAppToken(getExpiry(), getRefresh());

        final TokenProxy<EntityType.Application, TokenType.Simple> key = tokenRepository.insert(token);

        assertThat(tokenRepository.get(key), is(token));
    }

    @Test
    public void testUpdate() throws IOException {
        final AuthenticationToken<EntityType.Application, TokenType.Simple> token = getAppToken(getExpiry(), getRefresh());

        final TokenProxy<EntityType.Application, TokenType.Simple> key = tokenRepository.insert(token);

        final AuthenticationToken<EntityType.Application, TokenType.Simple> newToken = getAppToken(getExpiry().plusHours(1), getRefresh().plusHours(1));

        tokenRepository.update(key, newToken);

        assertThat(tokenRepository.get(key), is(newToken));
    }

    @Test
    public void testUpdateReturnsNullAndDoesNothingIfKeyNotPresent() throws IOException {
        final TokenProxy<EntityType.Application, TokenType.Simple> key = new TokenProxy<>(EntityType.Application.INSTANCE, TokenType.Simple.INSTANCE);

        final AuthenticationToken<EntityType.Application, TokenType.Simple> newToken = getAppToken(getExpiry(), getRefresh());

        final AuthenticationToken<EntityType.Application, TokenType.Simple> oldToken = tokenRepository.update(key, newToken);

        assertThat(oldToken, is(nullValue()));
        assertThat(tokenRepository.get(key), is(nullValue()));
    }

    @Test
    public void testRemove() throws IOException {
        final AuthenticationToken<EntityType.Application, TokenType.Simple> token = getAppToken(getExpiry(), getRefresh());

        final TokenProxy<EntityType.Application, TokenType.Simple> key = tokenRepository.insert(token);

        assertThat(tokenRepository.remove(key), is(token));
        assertThat(tokenRepository.get(key), is(nullValue()));
    }

    @Test
    public void testExpiryOnInsert() throws IOException, InterruptedException {
        final DateTime expiry1 = DateTime.now().plusSeconds(2);
        final DateTime expiry2 = DateTime.now().plusSeconds(12);

        final AuthenticationToken<EntityType.Application, TokenType.Simple> token1 = getAppToken(expiry1, getRefresh());
        final TokenProxy<EntityType.Application, TokenType.Simple> key1 = tokenRepository.insert(token1);

        final AuthenticationToken<EntityType.Application, TokenType.Simple> token2 = getAppToken(expiry2, getRefresh());
        final TokenProxy<EntityType.Application, TokenType.Simple> key2 = tokenRepository.insert(token2);

        TimeUnit.SECONDS.sleep(3L);

        assertThat(tokenRepository.get(key1), is(nullValue()));
        assertThat(tokenRepository.get(key2), is(token2));
    }

    @Test
    public void testExpiryOnUpdate() throws IOException, InterruptedException {
        final DateTime expiry1 = DateTime.now().plusSeconds(2);
        final DateTime expiry2 = DateTime.now().plusSeconds(5);
        final AuthenticationToken<EntityType.Application, TokenType.Simple> token1 = getAppToken(expiry1, getRefresh());

        final TokenProxy<EntityType.Application, TokenType.Simple> key = tokenRepository.insert(token1);

        final AuthenticationToken<EntityType.Application, TokenType.Simple> token2 = getAppToken(expiry2, getRefresh());
        tokenRepository.update(key, token2);

        TimeUnit.SECONDS.sleep(3L);
        assertThat(tokenRepository.get(key), is(token2));

        TimeUnit.SECONDS.sleep(3L);
        assertThat(tokenRepository.get(key), is(nullValue()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInsertThrowsIllegalArgumentExceptionForExpiredTokens() throws IOException {
        final AuthenticationToken<EntityType.Application, TokenType.Simple> token = getAppToken(THE_PAST, THE_PAST);

        tokenRepository.insert(token);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUpdateThrowsIllegalArgumentExceptionForExpiredTokens() throws IOException {
        final AuthenticationToken<EntityType.Application, TokenType.Simple> token1 = getAppToken(getExpiry(), getRefresh());
        final AuthenticationToken<EntityType.Application, TokenType.Simple> token2 = getAppToken(THE_PAST, THE_PAST);

        TokenProxy<EntityType.Application, TokenType.Simple> key = null;

        try {
            key = tokenRepository.insert(token1);
        } catch (final IllegalArgumentException e) {
            fail("The initial token should not have expired");
        }

        tokenRepository.update(key, token2);
    }

    @Test
    public void multipleTokenTypes() throws IOException {
        final AuthenticationToken<EntityType.Combined, TokenType.Simple> combinedToken = new AuthenticationToken<>(
            EntityType.Combined.INSTANCE,
            TokenType.Simple.INSTANCE,
            getExpiry(),
            "combined-id",
            "combined-secret",
            getRefresh()
        );

        final AuthenticationToken<EntityType.Developer, TokenType.HmacSha1> developerToken = new AuthenticationToken<>(
            EntityType.Developer.INSTANCE,
            TokenType.HmacSha1.INSTANCE,
            getExpiry(),
            "developer-id",
            "developer-secret",
            getRefresh()
        );

        final TokenProxy<EntityType.Combined, TokenType.Simple> combinedTokenProxy = tokenRepository.insert(combinedToken);
        final TokenProxy<EntityType.Developer, TokenType.HmacSha1> developerTokenProxy = tokenRepository.insert(developerToken);

        final AuthenticationToken<EntityType.Combined, TokenType.Simple> outputCombinedToken = tokenRepository.get(combinedTokenProxy);
        final AuthenticationToken<EntityType.Developer, TokenType.HmacSha1> outputDeveloperToken = tokenRepository.get(developerTokenProxy);

        assertThat(outputDeveloperToken, is(developerToken));
        assertThat(outputCombinedToken, is(combinedToken));
    }

    private AuthenticationToken<EntityType.Application, TokenType.Simple> getAppToken(final DateTime expiry, final DateTime refresh) {
        return new AuthenticationToken<>(
            EntityType.Application.INSTANCE,
            TokenType.Simple.INSTANCE,
            expiry,
            "foo",
            "bar",
            refresh
        );
    }

    private DateTime getExpiry() {
        return DateTime.now().plusHours(1);
    }

    private DateTime getRefresh() {
        return DateTime.now().plusMinutes(30);
    }
}