package dev.starcore.starcore.module.territory.rent;

/**
 * Exception thrown when rent payment fails
 */
public class RentPaymentFailedException extends RuntimeException {
    public RentPaymentFailedException(String message) {
        super(message);
    }
}
