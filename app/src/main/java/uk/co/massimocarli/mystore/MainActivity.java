package uk.co.massimocarli.mystore;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.*;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.widget.*;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.PublicKey;
import java.util.ArrayList;

import uk.co.massimocarli.mystore.util.Security;

public class MainActivity extends ActionBarActivity {

    /**
     * The KUS values for testing
     */
    private static interface Test {

        /**
         * The Sku for the successful purchase test
         */
        String TEST_PURCHASE = "android.test.purchased";

        /**
         * The Sku for the cancel purchase test
         */
        String TEST_CANCELED = "android.test.canceled";

        /**
         * The Sku for the refounded test
         */
        String TEST_REFUNDED = "android.test.refunded";

        /**
         * The Sku for the unavailable product test
         */
        String TEST_UNAVAILABLE = "android.test.item_unavailable";
    }

    /**
     * The Params we use for the requests
     */
    private static interface Const {

        /**
         * The OK response code
         */
        int RESPONSE_CODE_OK = 0;

        /**
         * The Version for the API
         */
        int API_VERSION = 3;

        /**
         * The package for the application
         */
        String PKG_NAME = "uk.co.massimocarli.mystore";

        /**
         * The In App product type
         */
        String IN_APP_TYPE = "inapp";

        /**
         * The Subscription product type
         */
        String SUBSCRIPTION_TYPE = "subs";

        /**
         * The key of the items id list
         */
        String ITEM_ID_LIST = "ITEM_ID_LIST";

        /**
         * The Response code
         */
        String RESPONSE_CODE = "RESPONSE_CODE";

        /**
         * The key for the DETAILS_LIST
         */
        String DETAILS_LIST = "DETAILS_LIST";

        /**
         * The key for the PendingIntent to launch for the purchase
         */
        String BUY_INTENT = "BUY_INTENT";

        /**
         * The key for the title
         */
        String TITLE = "title";

        /**
         * The key for the description
         */
        String DESCRIPTION = "description";

        /**
         * The key for the price
         */
        String PRICE = "price";

        /**
         * The key for the product id
         */
        String PRODUCT_ID = "productId";

        /**
         * The key for the product key
         */
        String PRODUCT_KEY = "type";

        /**
         * The key we use to get the purchase info
         */
        String INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";

        /**
         * THe list of purchased items
         */
        String INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST";

        /**
         * The key to get the information related to the data signature
         */
        String INAPP_DATA_SIGNATURE = "INAPP_DATA_SIGNATURE";

        /**
         * The key to get the purchase itemlist
         */
        String INAPP_PURCHASE_ITEM_LIST = "INAPP_PURCHASE_ITEM_LIST";

        /**
         * The key for the signature list
         */
        String INAPP_DATA_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST";

        /**
         * The key for the orderId after purchase
         */
        String ORDER_ID = "orderId";

    }

    /**
     * The Skus for the catalog products. This data should be managed using
     * a different service.
     */
    private interface SKUs {

        /**
         * The managed product SKU
         */
        String MANAGED_PROD_SKU = "uk_co_massimocarli_mystore_manages_product_1";

        /**
         * The not managed product
         */
        String UNMANAGED_PROD_SKU = "uk_co_massimocarli_mystore_unmanaged_product_2";

        /**
         * The subscription product SKU
         */
        String SUBSCRIPTION_PROD_SKU = "uk_co_massimocarli_mystore_subscription_3";

    }

    /**
     * The Tag for the log
     */
    private static final String TAG = MainActivity.class.getSimpleName();

    /**
     * The request id for the purchase Intent launch
     */
    private static final int PURCHASE_REQUEST_ID = 37;

    /**
     * The reference to the remote service
     */
    private IInAppBillingService mService;

    /**
     * The Progress dialog
     */
    private ProgressDialog mProgressDialog;

    /**
     * The current Model
     */
    private ArrayList<String> mModel = new ArrayList<String>();

    /**
     * The ListView for the products
     */
    private ListView mListView;

    /**
     * The Adapter we use to show the products
     */
    private ArrayAdapter<String> mAdapter;

    /**
     * The Signature
     */
    private String mSignatureBase64;

    /**
     * The Service Connection implementation to get a reference to the bound service
     */
    private final ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            showToast("Disconnected");
        }

        @Override
        public void onServiceConnected(ComponentName name,
                                       IBinder service) {
            mService = IInAppBillingService.Stub.asInterface(service);
            showToast("Service connected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Signature initialization
        mSignatureBase64 = getSignature();
        // The UI items
        mListView = (ListView) findViewById(R.id.product_list);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // We launch the dialog to ask for the purchase
                launchPurchase(position);
            }
        });
        // We create the Adapter
        mAdapter = new ArrayAdapter<String>(this, R.layout.product_list_item, mModel) {

            class Holder {

                TextView mProductTitle;

                TextView mProductDescription;

                TextView mProductPrice;

            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final Holder holder;
                if (convertView == null) {
                    convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.product_list_item, null);
                    holder = new Holder();
                    holder.mProductTitle = (TextView) convertView.findViewById(R.id.product_item_title);
                    holder.mProductDescription = (TextView) convertView.findViewById(R.id.product_item_description);
                    holder.mProductPrice = (TextView) convertView.findViewById(R.id.product_item_price);
                    convertView.setTag(holder);
                } else {
                    holder = (Holder) convertView.getTag();
                }
                // We set the value for the model (space for optimization here)
                final String jsonData = getItem(position);
                try {
                    final JSONObject jsonObj = new JSONObject(jsonData);
                    holder.mProductTitle.setText(jsonObj.optString(Const.TITLE));
                    holder.mProductDescription.setText(jsonObj.optString(Const.DESCRIPTION));
                    holder.mProductPrice.setText(jsonObj.optString(Const.PRICE));
                } catch (JSONException e) {
                    e.printStackTrace();
                    holder.mProductTitle.setText(R.string.no_data);
                    holder.mProductDescription.setText(R.string.no_data);
                    holder.mProductPrice.setText(R.string.no_data);
                }
                return convertView;
            }

        };
        mListView.setAdapter(mAdapter);
        // We bind the service
        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = new MenuInflater(this);
        menuInflater.inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_list_products:
                // We access the list of products
                requestProductList();
                break;
            case R.id.menu_test_purchase:
                // Here we test the purchase with success
                processPurchase(Const.IN_APP_TYPE, Test.TEST_PURCHASE);
                break;
            case R.id.menu_get_purchases:
                // We get the purchases
                getPurchases();
                break;
            case R.id.menu_test_consume:
                // We test the consume. Here we can change the purchaseToken
                launchConsume("inapp:uk.co.massimocarli.mystore:android.test.purchased");
                break;


        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mService != null) {
            unbindService(mServiceConn);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PURCHASE_REQUEST_ID) {
            if (resultCode == RESULT_OK) {
                // We get the information from the received data
                int responseCode = data.getIntExtra(Const.RESPONSE_CODE, 0);
                if (responseCode == Const.RESPONSE_CODE_OK) {
                    String purchaseData = data.getStringExtra(Const.INAPP_PURCHASE_DATA);
                    if (purchaseData == null) {
                        return;
                    }
                    String dataSignature = data.getStringExtra(Const.INAPP_DATA_SIGNATURE);
                    // We verify the order
                    if (!Security.verifyPurchase(mSignatureBase64, purchaseData, purchaseData)) {
                        showToast("Verification ERROR!");
                        return;
                    }
                    showToast("Verification Successful!");
                    // We get the purchase information
                    try {
                        JSONObject jsonObj = new JSONObject(purchaseData);
                        String sku = jsonObj.getString(Const.PRODUCT_ID);
                        String orderId = jsonObj.getString(Const.ORDER_ID);
                        showToast("Product Successfully Purchased" + sku + ":" + sku + " orderId:" + orderId);
                        // IMPORTANT: Remove this for security reason
                        Log.i(TAG, "DataSignature: " + dataSignature);
                    } catch (JSONException e) {
                        showToast("Something went wrong in the purchase process");
                        e.printStackTrace();
                    }
                } else {
                    showErrorMessage(responseCode);
                }
            } else {
                showToast("Purchase cancelled!");
            }
        }
    }

    /**
     * Utility method that shows a short toast message
     *
     * @param message The message to show in the toast
     */
    private void showToast(final String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * This is the utility method we use to request the product list. This must happen in a
     * background thread so we use an AsyncTask
     */
    private void requestProductList() {
        new AsyncTask<Void, Void, ArrayList<String>>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                getProgressDialog().show();
            }

            @Override
            protected ArrayList<String> doInBackground(Void... params) {
                ArrayList<String> skuList = new ArrayList<String>();
                skuList.add(SKUs.MANAGED_PROD_SKU);
                skuList.add(SKUs.UNMANAGED_PROD_SKU);
                skuList.add(SKUs.SUBSCRIPTION_PROD_SKU);
                skuList.add(Test.TEST_PURCHASE);
                Bundle querySkus = new Bundle();
                querySkus.putStringArrayList(Const.ITEM_ID_LIST, skuList);
                try {
                    // We access the service and get the result Bundle
                    Bundle skuDetails = mService.getSkuDetails(Const.API_VERSION, Const.PKG_NAME, Const.IN_APP_TYPE,
                            querySkus);
                    // We get the result code
                    int responseCode = skuDetails.getInt(Const.RESPONSE_CODE);
                    if (responseCode == Const.RESPONSE_CODE_OK) {
                        // We get the result data
                        ArrayList<String> responseList
                                = skuDetails.getStringArrayList(Const.DETAILS_LIST);
                        // We update the Model
                        mModel.clear();
                        mModel.addAll(responseList);
                        return responseList;
                    } else {
                        Log.e(TAG, "Error getting List " + responseCode);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(ArrayList<String> strings) {
                super.onPostExecute(strings);
                getProgressDialog().dismiss();
                if (strings == null) {
                    showToast("Something went wrong");
                } else {
                    showToast("Operation successful");
                    mAdapter.notifyDataSetChanged();
                }
            }
        }.execute();

    }

    private ProgressDialog getProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setTitle(R.string.progress_dialog_loading_products);
            mProgressDialog.setMessage(getString(R.string.progress_dialog_message));
            mProgressDialog.setCancelable(false);
            mProgressDialog.setIndeterminate(true);
        }
        return mProgressDialog;
    }

    /**
     * Launch the purchase for the given item
     *
     * @param position The position for the selected product
     */
    private void launchPurchase(final int position) {
        // We get the product to manage
        // We set the value for the model (space for optimization here)
        final String jsonData = mModel.get(position);
        try {
            final JSONObject jsonObj = new JSONObject(jsonData);
            final String productId = jsonObj.optString(Const.PRODUCT_ID);
            final String productType = jsonObj.optString(Const.PRODUCT_KEY);
            final AlertDialog alertDialog = new AlertDialog.Builder(this)
                    .setMessage(R.string.dialog_purchase_title)
                    .setTitle(jsonObj.optInt(Const.DESCRIPTION, R.string.no_data))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            processPurchase(productType, productId);
                        }
                    })
                    .create();
            alertDialog.show();
        } catch (JSONException e) {
            e.printStackTrace();
            showToast("Something went wrong!");
        }
    }

    /**
     * Shows the info related to the purchase
     */
    private void getPurchases() {
        new AsyncTask<Void, Void, Bundle>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                getProgressDialog().show();
            }

            @Override
            protected Bundle doInBackground(Void... params) {
                Bundle result = null;
                try {
                    result = mService.getPurchases(Const.API_VERSION, Const.PKG_NAME, Const.IN_APP_TYPE, null);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                return result;
            }

            @Override
            protected void onPostExecute(Bundle bundle) {
                super.onPostExecute(bundle);
                getProgressDialog().dismiss();
                if (bundle != null) {
                    // We manage the result
                    int responseCode = bundle.getInt(Const.RESPONSE_CODE);
                    if (responseCode == Const.RESPONSE_CODE_OK) {
                        // We get the result data
                        ArrayList<String> ownedSkus = bundle.getStringArrayList(
                                Const.INAPP_PURCHASE_ITEM_LIST);
                        ArrayList<String> purchaseDataList = bundle.getStringArrayList(
                                Const.INAPP_PURCHASE_DATA_LIST);
                        ArrayList<String> signatureList = bundle.getStringArrayList(
                                Const.INAPP_DATA_SIGNATURE_LIST);
                        Log.i(TAG, "INAPP_PURCHASE_ITEM_LIST: " + ownedSkus);
                        Log.i(TAG, "INAPP_PURCHASE_DATA_LIST: " + purchaseDataList);
                        Log.i(TAG, "INAPP_DATA_SIGNATURE_LIST: " + signatureList);
                    } else {
                        Log.e(TAG, "Error getting List " + responseCode);
                    }
                } else {
                    showToast("Something went wrong!");
                }
            }
        }.execute();

    }


    /**
     * Launch the actual process of the product id
     *
     * @param productType The type for the product to buy
     * @param productId   The id of the product to purchase
     */
    private void processPurchase(final String productType, final String productId) {
        // Here we calculate the Developer Payload
        final String developerPayload = getDevPayload(productId);
        // We get the PendingIntent to launch
        try {
            Bundle buyIntentBundle = mService.getBuyIntent(Const.API_VERSION, Const.PKG_NAME,
                    productId, productType, developerPayload);
            // We check for the result
            int responseCode = buyIntentBundle.getInt(Const.RESPONSE_CODE);
            if (responseCode == Const.RESPONSE_CODE_OK) {
                // We get the PendingIntent to launch
                PendingIntent pendingIntent = buyIntentBundle.getParcelable(Const.BUY_INTENT);
                // We launch the purchase
                startIntentSenderForResult(pendingIntent.getIntentSender(), PURCHASE_REQUEST_ID, new Intent(), 0, 0, 0);
            } else {
                showErrorMessage(responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
            showToast("Something went wrong!" + e.getMessage());
        }
    }


    /**
     * Utility method to test the consume of a product
     *
     * @param purchaseToken The purchaseToken for the product
     */
    public void launchConsume(final String purchaseToken) {


        new AsyncTask<Void, Void, Integer>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                getProgressDialog().show();
            }

            @Override
            protected Integer doInBackground(Void... params) {
                Integer resultCode = null;
                try {
                    resultCode = mService.consumePurchase(Const.API_VERSION, Const.PKG_NAME, purchaseToken);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                return resultCode;
            }


            @Override
            protected void onPostExecute(Integer responseCode) {
                super.onPostExecute(responseCode);
                getProgressDialog().dismiss();
                if (responseCode == Const.RESPONSE_CODE_OK) {
                    showToast("Product consumed successfully");
                } else {
                    showErrorMessage(responseCode);
                }
            }
        }.execute();
    }

    /**
     * Here we encapsulate all the logic that permits us to get a String from the productId
     * and other information.
     *
     * @param productId The product Id
     * @return The String to use as Developer Payload
     */
    private String getDevPayload(final String productId) {
        // We just return the same string with a prefix.
        return "DevPayload" + productId;
    }


    /**
     * Utility method that shows a Toast with the description of the message from the service
     *
     * @param errorCode The error code
     */
    private void showErrorMessage(final int errorCode) {
        switch (errorCode) {
            case 1:
                showToast("RESULT_USER_CANCELED: user pressed back or canceled a dialog");
                break;
            case 3:
                showToast("RESULT_BILLING_UNAVAILABLE: this billing API version is not supported");
                break;
            case 4:
                showToast("RESULT_ITEM_UNAVAILABLE: requested SKU is not available for purchase");
                break;
            case 5:
                showToast("RESULT_DEVELOPER_ERROR: invalid arguments provided to the API");
                break;
            case 6:
                showToast("RESULT_ERROR: Fatal error during the API action");
                break;
            case 7:
                showToast("RESULT_ITEM_ALREADY_OWNED: Failure to purchase since item is already owned");
                break;
            case 8:
                showToast("RESULT_ITEM_NOT_OWNED: Failure to consume since item is not owned");
                break;
            default:
                showToast("Unknown error!");
        }
    }


    /**
     * @return The Signature Base 64
     */
    private String getSignature() {
        return "<YOUR SIGNATURE>";
    }

}
