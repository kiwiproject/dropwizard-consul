package com.example.helloworld.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.Length;

public final class Saying {
    private final long id;

    @Length(max = 3)
    private final String content;

    @JsonCreator
    public Saying(@JsonProperty("id") long id, @JsonProperty("content") String content) {
        this.id = id;
        this.content = content;
    }

    @JsonProperty
    public long getId() {
        return id;
    }

    @JsonProperty
    public String getContent() {
        return content;
    }
}
