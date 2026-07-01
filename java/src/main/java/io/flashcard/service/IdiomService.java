package io.flashcard.service;

import io.flashcard.model.Idiom;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class IdiomService {

    private static final List<Idiom> IDIOMS = List.of(
        new Idiom("Iga algus on raske.", "Every beginning is hard.", "Starting something new is always difficult."),
        new Idiom("Harjutamine teeb meistriks.", "Practice makes a master.", "Practice makes perfect."),
        new Idiom("Kes otsib, see leiab.", "Who seeks, finds.", "If you look for something, you'll find it."),
        new Idiom("Hommik on õhtust targem.", "Morning is wiser than evening.", "Sleep on it before making decisions."),
        new Idiom("Üheksa korda mõõda, üks kord lõika.", "Measure nine times, cut once.", "Think carefully before acting."),
        new Idiom("Kus suitsu, seal tuld.", "Where there's smoke, there's fire.", "Rumors usually have some truth."),
        new Idiom("Aeg on raha.", "Time is money.", "Don't waste time."),
        new Idiom("Kes teisele auku kaevab, see ise sisse kukub.", "Who digs a hole for another falls in themselves.", "Karma — what goes around comes around."),
        new Idiom("Õnn tuleb magades.", "Luck comes while sleeping.", "Good things come to those who wait."),
        new Idiom("Küla hull on küla ilu.", "The village fool is the village's beauty.", "Every community needs its characters."),
        new Idiom("Oma silm on kuningas.", "Your own eye is king.", "Seeing is believing."),
        new Idiom("Rumal rääkigu palju, tark kuulab ja teab.", "The fool talks a lot, the wise listens and knows.", "Listen more than you speak."),
        new Idiom("Igal oinal oma mihklipäev.", "Every ram has its Michaelmas.", "Everyone gets what's coming to them."),
        new Idiom("Vana karu ei tantsi.", "An old bear doesn't dance.", "Old habits die hard."),
        new Idiom("Kes kannatab, see kaua elab.", "Who endures, lives long.", "Patience is a virtue.")
    );

    public List<Idiom> getAllIdioms() {
        return IDIOMS;
    }

    public Idiom getRandomIdiom() {
        return IDIOMS.get(ThreadLocalRandom.current().nextInt(IDIOMS.size()));
    }
}
