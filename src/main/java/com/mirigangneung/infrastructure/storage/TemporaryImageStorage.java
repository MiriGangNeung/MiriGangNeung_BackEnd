package com.mirigangneung.infrastructure.storage;
import java.io.*; import java.time.*;
public interface TemporaryImageStorage { String save(InputStream input,String contentType,long size,Instant expiresAt) throws IOException; InputStream open(String key) throws IOException; void delete(String key) throws IOException; boolean exists(String key); }
