package com.myfamilygallery.model;

public record ImportResult(int imported, int duplicates, int skipped, String message) {}
