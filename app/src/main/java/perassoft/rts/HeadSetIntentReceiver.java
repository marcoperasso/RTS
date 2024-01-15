package perassoft.rts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

public class HeadSetIntentReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null && action.equals(Intent.ACTION_MEDIA_BUTTON)) {
            KeyEvent ke = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (ke != null && ke.getAction() == KeyEvent.ACTION_DOWN  && PlayerService.isServiceRunning()) {
                Intent i = new Intent(context, PlayerService.class);
                i.setAction(PlayerService.ACTION_PAUSE_LISTEN);
                context.startService(i);
            }
        }
    }
}
