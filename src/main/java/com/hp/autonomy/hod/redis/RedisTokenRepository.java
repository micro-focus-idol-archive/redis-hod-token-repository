/*
 * Copyright 2015 Hewlett-Packard Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */

package com.hp.autonomy.hod.redis;

import com.hp.autonomy.hod.client.api.authentication.AuthenticationToken;
import com.hp.autonomy.hod.client.api.authentication.EntityType;
import com.hp.autonomy.hod.client.api.authentication.TokenType;
import com.hp.autonomy.hod.client.token.TokenProxy;
import com.hp.autonomy.hod.client.token.TokenRepository;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;
import redis.clients.util.Pool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Implementation of {@link TokenRepository} backed by a Redis instance
 *
 * This repository expires old tokens
 */
public class RedisTokenRepository implements TokenRepository {

    private final Pool<Jedis> jedisPool;

    /**
     * Creates a new RedisTokenRepository
     * @param jedisPool The pool of Jedis connections to use. This should be shut down externally when no longer needed
     */
    public RedisTokenRepository(final Pool<Jedis> jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public <E extends EntityType, T extends TokenType> TokenProxy<E, T> insert(final AuthenticationToken<E, T> authenticationToken) throws IOException {
        checkTokenExpiry(authenticationToken);

        try(final Jedis jedis = jedisPool.getResource()) {
            final TokenProxy<E, T> key = new TokenProxy<>(authenticationToken.getEntityType(), authenticationToken.getTokenType());

            jedis.setex(serialize(key), getExpirySeconds(authenticationToken.getExpiry()), serialize(authenticationToken));

            return key;
        }
    }

    @Override
    public <E extends EntityType, T extends TokenType> AuthenticationToken<E, T> update(final TokenProxy<E, T> tokenProxy, final AuthenticationToken<E, T> authenticationToken) throws IOException {
        checkTokenExpiry(authenticationToken);

        try(final Jedis jedis = jedisPool.getResource()) {
            final byte[] keyBytes = serialize(tokenProxy);

            final Transaction transaction = jedis.multi();

            final Response<byte[]> oldTokenResponse = transaction.get(keyBytes);

            transaction.set(keyBytes, serialize(authenticationToken), "XX".getBytes(), "EX".getBytes(), getExpirySeconds(authenticationToken.getExpiry()));

            transaction.exec();

            //noinspection unchecked
            return (AuthenticationToken<E, T>) deserialize(oldTokenResponse.get());
        }
    }

    @Override
    public <E extends EntityType, T extends TokenType> AuthenticationToken<E, T> get(final TokenProxy<E, T> tokenProxy) throws IOException {
        try(final Jedis jedis = jedisPool.getResource()) {
            final byte[] bytes = jedis.get(serialize(tokenProxy));

            //noinspection unchecked
            return (AuthenticationToken<E, T>) deserialize(bytes);
        }
    }

    @Override
    public <E extends EntityType, T extends TokenType> AuthenticationToken<E, T> remove(final TokenProxy<E, T> tokenProxy) throws IOException {
        try(final Jedis jedis = jedisPool.getResource()) {
            final byte[] keyBytes = serialize(tokenProxy);

            final Transaction transaction = jedis.multi();

            final Response<byte[]> oldTokenResponse = transaction.get(keyBytes);
            transaction.del(keyBytes);

            transaction.exec();

            //noinspection unchecked
            return (AuthenticationToken<E, T>) deserialize(oldTokenResponse.get());
        }
    }

    private void checkTokenExpiry(final AuthenticationToken<?, ?> authenticationToken) {
        if(authenticationToken.hasExpired()) {
            throw new IllegalArgumentException("Token has already expired");
        }
    }

    private byte[] serialize(final Serializable serializable) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(serializable);

        return byteArrayOutputStream.toByteArray();
    }

    private Object deserialize(final byte[] bytes) throws IOException {
        if (bytes == null) {
            return null;
        }

        try {
            final ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes));

            return objectInputStream.readObject();
        } catch (final ClassNotFoundException e) {
            throw new AssertionError("Required classes are not available", e);
        }
    }

    private int getExpirySeconds(final DateTime dateTime) {
        return Seconds.secondsBetween(DateTime.now(), dateTime).getSeconds();
    }
}
