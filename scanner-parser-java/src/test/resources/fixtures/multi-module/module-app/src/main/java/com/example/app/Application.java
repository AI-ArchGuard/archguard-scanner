package com.example.app;

import com.example.api.Port;
import com.example.infra.Repository;

public final class Application implements Port {
    private final Repository repository;

    public Application(Repository repository) {
        this.repository = repository;
    }
}
