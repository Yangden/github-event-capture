package com.example.github_event_capture.utils;

/* wrap up results of user services */
public class Result<T> {
    private boolean success;
    private String message;
    T data;

    private Result(boolean success, String message){
        this.success = success;
        this.message = message;
    }

    private Result(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static Result success(String message) {
        return new Result(true, message);
    }

    public static Result success(String message, Object data) {
        return new Result(true, message, data);
    }

    public static Result fail(String message) {
        return new Result(false, message);
    }

    // getters
    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
}
