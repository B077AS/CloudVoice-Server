package main;

import java.util.ArrayList;
import java.util.List;

public class SpeakingSubject {
    private List<SpeakingObserver> observers = new ArrayList<>();
    private boolean speaking;

    public void addObserver(SpeakingObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(SpeakingObserver observer) {
        observers.remove(observer);
    }

    public void setSpeaking(boolean newSpeakingValue) {
        if (speaking != newSpeakingValue) {
            speaking = newSpeakingValue;
            notifyObservers();
        }
    }

    private void notifyObservers() {
        for (SpeakingObserver observer : observers) {
            observer.speakingChanged(speaking);
        }
    }
}
