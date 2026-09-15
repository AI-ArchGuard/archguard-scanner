package com.example;

@Marker
public final class Service implements Port<Audit> {
    private final Repository repository;

    public Service(Repository repository) {
        this.repository = repository;
    }

    public Repository repository() {
        return repository;
    }

    public static class Nested extends Repository {}
}
