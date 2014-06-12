package ie.davidmoloney.android;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import com.evrythng.thng.resource.model.store.Property;
import com.evrythng.thng.resource.model.store.Thng;
import ie.davidmoloney.evrythng.EvrythngManager;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

/**
 * NFC Code based on sample by
 * @author Ralf Wondratschek
 */
public class MainActivity extends Activity {

    public static final String MIME_TEXT_PLAIN = "text/plain";
    public static final String TAG = "MainActivity";
    public static final String API_KEY = "";
    private TextView mainTextView;
    private EditText editText;
    private NfcAdapter nfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        if (isPlainText(intent)) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            new NdefReaderTask().execute(tag);
        } else {
            Log.d(TAG, "Only handles plain-text NFC tags");
        }
    }

    private boolean isPlainText(final Intent intent) {
        return MIME_TEXT_PLAIN.equals(intent.getType());
    }

    private void initViews() {
        mainTextView = (TextView) findViewById(R.id.textView_explanation);
        editText = (EditText) findViewById(R.id.assignee);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        assignInitialMessaging();
    }

    private void assignInitialMessaging() {
        if (!nfcAdapter.isEnabled()) {
            mainTextView.setText(R.string.nfcDisabled);
        } else {
            mainTextView.setText(R.string.helpText);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupForegroundDispatch(this, nfcAdapter);
    }

    /**
     * @param activity The corresponding {@link Activity} requesting the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);

        IntentFilter[] filters = new IntentFilter[1];
        String[][] techList = new String[][]{};

        // Notice that this is the same filter as in our manifest.
        filters[0] = new IntentFilter();
        filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filters[0].addCategory(Intent.CATEGORY_DEFAULT);
        try {
            filters[0].addDataType(MIME_TEXT_PLAIN);
        } catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("Unsupported MimeType", e);
        }

        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
    }

    @Override
    protected void onPause() {
        stopForegroundDispatch(this, nfcAdapter);
        super.onPause();
    }

    /**
     * @param activity The corresponding {@link } requesting to stop the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    /**
     * Background task for reading the data. Do not block the UI thread while reading. 
     *
     * @author Ralf Wondratschek
     *
     */
    private class NdefReaderTask extends AsyncTask<Tag, Void, String> {

        @Override
        protected String doInBackground(Tag... params) {

            for (NdefRecord ndefRecord : getRecords(params[0])) {
                if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
                    try {
                        String tagValue = readText(ndefRecord);
                        return buildResponse(tagValue);
                    } catch (UnsupportedEncodingException e) {
                        Log.e(TAG, "Error reading tag", e);
                    }
                }
            }
            return null;
        }

        private String readText(NdefRecord record) throws UnsupportedEncodingException {
        /*
         * See NFC forum specification for "Text Record Type Definition" at 3.2.1 
         * 
         * http://www.nfc-forum.org/specs/
         * 
         * bit_7 defines encoding
         * bit_6 reserved for future use, must be 0
         * bit_5..0 length of IANA language code
         */

            byte[] payload = record.getPayload();

            // Get the Text Encoding
            String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";

            // Get the Language Code
            int languageCodeLength = payload[0] & 0063;

            // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
            // e.g. "en"

            // Get the Text
            return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
        }

        private NdefRecord[] getRecords(Tag tag) {
            Ndef ndef = Ndef.get(tag);
            if (isSupported(ndef)) {
                return null;
            }

            NdefMessage ndefMessage = ndef.getCachedNdefMessage();
            return ndefMessage.getRecords();
        }

        private boolean isSupported(final Ndef ndef) {
            if (ndef == null) {
                // NDEF is not supported by this Tag. 
                return true;
            }
            return false;
        }

        private String buildResponse(final String tagValue) {
            EvrythngManager evrythngManager = new EvrythngManager(API_KEY);
            Thng remoteThng = evrythngManager.retrieveThng(tagValue);
            StringBuilder stringBuilder = new StringBuilder();
            String person = editText.getText().toString();
            stringBuilder.append(Html.fromHtml("<h2>" + remoteThng.getName() + "</h2>"));
            Spanned label = Html.fromHtml("<strong>Currently with:</strong> "); 

            if(hasNewUserSubmitted(person)) {
                updateProperty(evrythngManager, remoteThng, person);
                label = Html.fromHtml("<strong>Passing to:</strong> ");
            }

            stringBuilder.append(label);
            Property latestOwner = getLatestOwner(evrythngManager, remoteThng);
            stringBuilder.append(Html.fromHtml(latestOwner.getValue() + " <em>(" + getDate(latestOwner.getTimestamp()) + ")</em>"));

            return stringBuilder.toString();
        }

        private Property getLatestOwner(final EvrythngManager evrythngManager, final Thng remoteThng) {
            return evrythngManager.getPropertiesOfThng(remoteThng.getId(), "person").get(0);
        }

        private boolean hasNewUserSubmitted(final String person) {
            return person.length() > 0;
        }

        private void updateProperty(final EvrythngManager evrythngManager, final Thng remoteThng, final String person) {
            evrythngManager.setProperty(remoteThng.getId(), "person", person, System.currentTimeMillis());
        }

        private String getDate(long time) {
            Calendar cal = Calendar.getInstance(Locale.ENGLISH);
            cal.setTimeInMillis(time);
            return DateFormat.format("hh:mma, dd MMM", cal).toString();
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                mainTextView.setText(result);
            }
        }
    }

}
