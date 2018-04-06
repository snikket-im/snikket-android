package eu.siacs.conversations.ui.util;

/**
 * Created by mxf on 2018/4/3.
 */

public class PendingActionHelper {

    private PendingAction pendingAction;

    public void push(PendingAction pendingAction) {
        this.pendingAction = pendingAction;
    }

    public void execute() {
        if(pendingAction != null){
            pendingAction.execute();
            pendingAction = null;
        }
    }

    public void undo() {
        pendingAction = null;
    }

    public interface PendingAction {
        void execute();
    }
}
