package perassoft.rts;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class HeadSetIntentReceiver extends BroadcastReceiver {
    private boolean firstTimeForMusicIntentReceiver = true;

    public HeadSetIntentReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null) {
            switch (action) {
                case Intent.ACTION_HEADSET_PLUG:
                    if (firstTimeForMusicIntentReceiver) {
                        firstTimeForMusicIntentReceiver = false;
                        return;
                    }
                    int state = intent.getIntExtra("state", -1);
                    if (state == 0 && PlayerService.isServicePlaying()) {
                        playOrPause(context);
                    }
                    break;
                case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    if (PlayerService.isServicePlaying())
                        playOrPause(context);
                    break;
            }
        }
    }

    private static void playOrPause(Context context) {
        Intent i = new Intent(context, PlayerService.class);
        i.setAction(PlayerService.ACTION_PAUSE_LISTEN);
        context.startService(i);
    }
}
