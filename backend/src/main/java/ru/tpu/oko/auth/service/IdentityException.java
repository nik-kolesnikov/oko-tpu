package ru.tpu.oko.auth.service;

/** Провайдер не подтвердил личность: code неверный, просрочен или уже использован. */
public class IdentityException extends RuntimeException {

    public IdentityException(String message) {
        super(message);
    }
}
