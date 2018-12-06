package io.mywish.airdrop.exception;

public class UnlockAddressException extends RuntimeException {
    public UnlockAddressException() {
    }

    public UnlockAddressException(String message) {
        super(message);
    }

    public UnlockAddressException(String message, Throwable cause) {
        super(message, cause);
    }
}
