package xtr.keymapper.profiles;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A one-shot result, independent of profile enabled state. */
public final class ProfileDialogOperation {
    public enum Outcome { Accept, Decline, Dismiss }
    private final BooleanSupplier valid;
    private final Consumer<Outcome> listener;
    private boolean finished;

    public ProfileDialogOperation(BooleanSupplier valid, Consumer<Outcome> listener) {
        this.valid = valid;
        this.listener = listener;
    }

    public void finish(Outcome outcome) {
        if (finished) return;
        finished = true;
        listener.accept(valid.getAsBoolean() ? outcome : Outcome.Dismiss);
    }
}
