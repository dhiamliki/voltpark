package tn.voltpark.energy.service;

/** Mesure incoherente (valeur negative, energie qui diminue, soc hors 0-100...). */
public class InvalidReadingException extends RuntimeException {

    public InvalidReadingException(String message) {
        super(message);
    }
}
