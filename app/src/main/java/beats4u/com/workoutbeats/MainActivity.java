package beats4u.com.workoutbeats;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerNotificationCallback;
import com.spotify.sdk.android.player.PlayerState;
import com.spotify.sdk.android.player.Spotify;
import com.thalmic.myo.AbstractDeviceListener;
import com.thalmic.myo.Arm;
import com.thalmic.myo.DeviceListener;
import com.thalmic.myo.Hub;
import com.thalmic.myo.Myo;
import com.thalmic.myo.Quaternion;
import com.thalmic.myo.XDirection;
import com.thalmic.myo.scanner.ScanActivity;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements
        PlayerNotificationCallback, ConnectionStateCallback {

    // TODO: Replace with your client ID
    private static final String CLIENT_ID = "190ac8b5264143f2b9f9f145e69d7e3e";
    // TODO: Replace with your redirect URI
    private static final String REDIRECT_URI = "beats4u://callback";
    // Request code that will be used to verify if the result comes from correct activity
    // Can be any integer
    private static final int REQUEST_CODE = 1337;
    private static final String TAG = "SpotifiyAuth";
    private static final double WALKING_TIME = 30;
    private static final double RUNNING_TIME = 100;
    private static final long WAKEUP_INTERVAL = 5000;
    private static String curr_track_id = "3GTXok0dIm0mMqBiVklBYS";

    private Player mPlayer;

    private TextView titleView;
    private ImageView imageView;
    private double t = 0;

    //stores most recent min and max values
    private static double min_val = 90;
    private static double max_val = -90;

    private double diffForSong;
    private boolean workoutStarted = false;

    private ImageView statusImage;
    private boolean connected = false;

    Drawable tempImageSrc;

    private enum Mode  {
        WALKING, JOGGING, RUNNING, DEFAULT
    }

    private static Mode mode = Mode.DEFAULT;
    TimerTask timerTask;

        // For Myo
    private DeviceListener mlistener =  new AbstractDeviceListener() {
        // onConnect() is called whenever a Myo has been connected.
        @Override
        public void onConnect(Myo myo, long timestamp) {
            statusImage.setImageResource(R.drawable.green_light);
            statusImage.bringToFront();
        }

        // onDisconnect() is called whenever a Myo has been disconnected.
        @Override
        public void onDisconnect(Myo myo, long timestamp) {
            statusImage.setImageResource(R.drawable.red_light);
            statusImage.bringToFront();
        }

        // onArmSync() is called whenever Myo has recognized a Sync Gesture after someone has put it on their
        // arm. This lets Myo know which arm it's on and which way it's facing.
        @Override
        public void onArmSync(Myo myo, long timestamp, Arm arm, XDirection xDirection) {
        }

        // onArmUnsync() is called whenever Myo has detected that it was moved from a stable position on a person's arm after
        // it recognized the arm. Typically this happens when someone takes Myo off of their arm, but it can also happen
        // when Myo is moved around on the arm.
        @Override
        public void onArmUnsync(Myo myo, long timestamp) {
        }

        // onUnlock() is called whenever a synced Myo has been unlocked. Under the standard locking
        // policy, that means poses will now be delivered to the listener.
        @Override
        public void onUnlock(Myo myo, long timestamp) {
            titleView.setText(R.string.unlocked);
        }

        // onLock() is called whenever a synced Myo has been locked. Under the standard locking
        // policy, that means poses will no longer be delivered to the listener.
        @Override
        public void onLock(Myo myo, long timestamp) {
            titleView.setText(R.string.locked);
        }

        // onOrientationData() is called whenever a Myo provides its current orientation,
        // represented as a quaternion.
        @Override
        public void onOrientationData(Myo myo, long timestamp, Quaternion rotation) {
            if (! connected) {
                connected = true;
            }
            // Calculate Euler angles (roll, pitch, and yaw) from the quaternion.
            float roll = (float) Math.toDegrees(Quaternion.roll(rotation));
            float pitch = (float) Math.toDegrees(Quaternion.pitch(rotation));
            float yaw = (float) Math.toDegrees(Quaternion.yaw(rotation));
            //Log.d(TAG, "roll: " + roll + "pitch: " + pitch + "yaw: " + yaw);
            //Log.d(TAG, "x: " + rotation.x() + " y: " + rotation.y() +  " z: " + rotation.z());
            double x = rotation.x();
            double y = rotation.y();

            if (t > 5 &&  pitch < min_val) {
                min_val = pitch;
            }

            if (t > 5 && pitch > max_val) {
                max_val = pitch;
            }

            //xSeries.appendData(new DataPoint(t, pitch), true, t_max);
            t += 1;

            // Adjust roll and pitch for the orientation of the Myo on the arm.
            if (myo.getXDirection() == XDirection.TOWARD_ELBOW) {
                roll *= -1;
                pitch *= -1;
            }
        }

    };
    private LineGraphSeries<DataPoint> xSeries;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //graphInit();
        titleView = (TextView) findViewById(R.id.myTitle);
        imageView = (ImageView) findViewById(R.id.pictureImage);
        imageView.setImageResource((R.drawable.bitcamp));

        timerTask = new TimerTask() {
            @Override
            public void run() {
                diffForSong = Math.abs(max_val - min_val);
                min_val = 90;
                max_val = -90;
                if (diffForSong != 180) checkMovementType(diffForSong);
            }
        };

        initializeHub();
        spotifyLogin();
        statusImage = (ImageView) findViewById(R.id.status_light);
        statusImage.setImageResource(R.drawable.red_light);
        statusImage.bringToFront();

        //getActionBar().setIcon(R.drawable.my_icon);
    }

    private void graphInit() {
        xSeries = new LineGraphSeries<>();
        GraphView graph = null;// = (GraphView) findViewById(R.id.graph);
        graph.addSeries(xSeries);
        xSeries.setColor(Color.GREEN);

        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(100);
    }

    private void spotifyLogin() {
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
    }

    private void checkMovementType(double diffForSong) {
       if (mode != Mode.WALKING && diffForSong <= WALKING_TIME) {
           mode = Mode.WALKING;
           if (! mPlayer.isShutdown() ) {
               mPlayer.play("spotify:user:123398983:playlist:66OgOHEL6OJlY02ClEvmF1");
           }
           Log.d(TAG, "walking song");
       } else if (mode != Mode.JOGGING && diffForSong > WALKING_TIME && diffForSong <= RUNNING_TIME) {
           mode = Mode.JOGGING;
           if (! mPlayer.isShutdown() ) {
               mPlayer.play("spotify:user:spotify:playlist:5p9ILyu1wb4KKHORoXU8nb");
           }

           Log.d(TAG, "jogging song");
        } else if (mode != Mode.RUNNING && diffForSong > RUNNING_TIME) {
           mode = Mode.RUNNING;
           if (! mPlayer.isShutdown() ) {
               mPlayer.play("spotify:user:spotify:playlist:6RsopNg2yrLjKiu00jaCyi");
           }
           Log.d(TAG, "running song");
       }
    }

    private void initializeHub() {
        Hub hub = Hub.getInstance();
        Hub.getInstance().setLockingPolicy(Hub.LockingPolicy.NONE);

        if (!hub.init(this)) {
            Toast.makeText(this, "Couldn't initialize Hub", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Hub.getInstance().setLockingPolicy(Hub.LockingPolicy.NONE);

        hub.addListener(mlistener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d("MainActivity", "onCreateOptionsMenu");
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d("MainActivity", "onOptionsItemSelected");
        int id = item.getItemId();
        if (R.id.action_scan == id) {
            onScanActionSelected();
            return true;
        } else if (R.id.action_logout == id) {
            AuthenticationClient.clearCookies(this);
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private void onScanActionSelected() {
        // Launch the ScanActivity to scan for Myos to connect to.
        Intent intent = new Intent(this, ScanActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                Config playerConfig = new Config(this, response.getAccessToken(), CLIENT_ID);
                Spotify.getPlayer(playerConfig, this, new Player.InitializationObserver() {
                    @Override
                    public void onInitialized(Player player) {
                        mPlayer = player;
                        mPlayer.addConnectionStateCallback(MainActivity.this);
                        mPlayer.addPlayerNotificationCallback(MainActivity.this);
                        mPlayer.play("spotify:track:3GTXok0dIm0mMqBiVklBYS");
                        mPlayer.setShuffle(true);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e("MainActivity", "Could not initialize player: " + throwable.getMessage());
                    }
                });
            }
        }
    }

    @Override
    public void onLoggedIn() {
        Log.d("MainActivity", "User logged in");
    }

    @Override
    public void onLoggedOut() {
        Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Throwable error) {
        Log.d("MainActivity", "Login failed");
        spotifyLogin();
    }

    @Override
    public void onTemporaryError() {
        Log.d("MainActivity", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("MainActivity", "Received connection message: " + message);
    }

    @Override
    public void onPlaybackEvent(EventType eventType, PlayerState playerState) {
        Log.d("MainActivity", "Playback event received: " + eventType.name());
        if (eventType.name().equals("TRACK_CHANGED")) {
            curr_track_id = playerState.trackUri;
            Log.d(TAG, "trackUri: " + playerState.trackUri.substring(14));
            String url = "https://api.spotify.com/v1/tracks/" + curr_track_id.substring(14);
            (new NetworkTasks(false)).execute(url);
        }
    }

    @Override
    public void onPlaybackError(ErrorType errorType, String errorDetails) {
        Log.d("MainActivity", "Playback error received: " + errorType.name());
    }

    @Override
    protected void onDestroy() {
        Hub.getInstance().removeListener(mlistener);
        if (isFinishing()) Hub.getInstance().shutdown();
        Spotify.destroyPlayer(this);
        super.onDestroy();
    }

    public void showScan(View view) {
        onScanActionSelected();
    }

    public void startWorkout(View view) {
        if (! connected) return;
        if (! workoutStarted) {
            Log.d(TAG, "starting workout");
            mode = Mode.DEFAULT;
            Timer timer = new Timer();
            timer.scheduleAtFixedRate(timerTask, 300, WAKEUP_INTERVAL);
            workoutStarted = true;
        }
    }


    private class NetworkTasks extends AsyncTask<String, String, String> {
        private boolean forPicture;

        public NetworkTasks(boolean forPicture) {
            this.forPicture = forPicture;
        }

        @Override
        protected String  doInBackground(String... url) {
            try {
                if (!forPicture) {
                    return (new HttpsClient()).testIt(url[0]);
                } else {
                    InputStream is = (InputStream) new URL(url[0]).getContent();
                    tempImageSrc = Drawable.createFromStream(is, "src name");
                    return null;
                }
                //return (new HttpsClient()).sendGet(url[0]);

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String response) {
            if (forPicture) {
                displayImageFromWebOperations();
            } else {
                processResponse(response);
            }
        }
    }

    private void processResponse(String response){
        try {
            JSONObject jsonObject = new JSONObject(response);
            String  imageUrl = jsonObject.getJSONObject("album").getJSONArray("images").getJSONObject(0).getString("url");
            String artist = jsonObject.getJSONArray("artists").getJSONObject(0).getString("name");
            String title = jsonObject.getString("name");

            Log.d(TAG, artist + " - " + title);
            titleView.setText(artist + " - " + title);

            (new NetworkTasks(true)).execute(imageUrl);
            Log.d(TAG, "url: " + imageUrl);
        }catch (Exception e) {
            Log.d(TAG, "Exception, image not set");
            imageView.setImageResource((R.drawable.bitcamp));
            e.printStackTrace();
        }
    }

    public void displayImageFromWebOperations() {
        if (tempImageSrc != null) {
            imageView.setImageDrawable(tempImageSrc);
            Log.d(TAG, "image set");
        } else {
            Log.d(TAG, "image  not set, drawable null");
            imageView.setImageResource((R.drawable.bitcamp));
        }
    }
}
