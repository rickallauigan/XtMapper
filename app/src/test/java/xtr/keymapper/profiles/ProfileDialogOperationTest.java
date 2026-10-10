package xtr.keymapper.profiles;

import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.*;
import static xtr.keymapper.profiles.ProfileDialogOperation.Outcome.*;

public class ProfileDialogOperationTest {
    @Test public void declineAndDismissAreTerminalEvenIfAnAcceptWasQueued() {
        for (var outcome : new ProfileDialogOperation.Outcome[]{Decline, Dismiss}) {
            var results = new ArrayList<ProfileDialogOperation.Outcome>();
            var operation = new ProfileDialogOperation(() -> true, results::add);
            operation.finish(outcome); operation.finish(Accept); operation.finish(Dismiss);
            assertEquals(java.util.List.of(outcome), results);
        }
    }
    @Test public void invalidOwnerCannotAcceptAndAcceptIsDeliveredOnce() {
        var results = new ArrayList<ProfileDialogOperation.Outcome>();
        new ProfileDialogOperation(() -> false, results::add).finish(Accept);
        assertEquals(java.util.List.of(Dismiss), results);
        var operation = new ProfileDialogOperation(() -> true, results::add);
        operation.finish(Accept); operation.finish(Accept);
        assertEquals(java.util.List.of(Dismiss, Accept), results);
    }
}
