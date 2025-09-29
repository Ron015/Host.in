package ron.jnv.internet;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.ConnectivityManager.NetworkCallback;
import android.os.Bundle;
import android.os.Handler;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends Activity {
    private WebView mWebView;
    private NetworkCallback networkCallback;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Handler timeoutHandler = new Handler();

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        mWebView = findViewById(R.id.activity_main_webview);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        // Custom WebViewClient to block other domains + handle errors
        mWebView.setWebViewClient(new HostOnlyWebViewClient(this) {
            @Override
            public void onPageFinished(WebView view, String url) {
                swipeRefreshLayout.setRefreshing(false); // stop refresh spinner
            }
        });

        // Pull to refresh
        swipeRefreshLayout.setOnRefreshListener(() -> loadWithTimeout("http://host.in", 10000));

        if (isNetworkAvailable()) {
            loadWithTimeout("http://host.in", 10000); // 10 sec timeout
        } else {
            mWebView.loadUrl("file:///android_asset/offline.html");
        }

        networkCallback = new NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                runOnUiThread(() -> loadWithTimeout("http://host.in", 10000));
            }

            @Override
            public void onLost(Network network) {
                runOnUiThread(() -> mWebView.loadUrl("file:///android_asset/offline.html"));
            }
        };
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        connectivityManager.registerDefaultNetworkCallback(networkCallback);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network nw = connectivityManager.getActiveNetwork();
        if (nw == null) return false;
        NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(nw);
        return actNw != null && (
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                        actNw.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        );
    }

    // WebViewClient that blocks all domains except host.in
    private static class HostOnlyWebViewClient extends WebViewClient {
        private final Activity activity;

        HostOnlyWebViewClient(Activity activity) {
            this.activity = activity;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            return !url.contains("host.in"); // allow only host.in
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            view.loadUrl("file:///android_asset/timeout.html");
        }
    }

    // Function to load with timeout
    private void loadWithTimeout(String url, int timeoutMillis) {
        mWebView.loadUrl(url);

        timeoutHandler.postDelayed(() -> {
            if (mWebView.getProgress() < 100) {
                // Page abhi tak load complete nahi hua
                mWebView.stopLoading();
                mWebView.loadUrl("file:///android_asset/timeout.html");
            }
        }, timeoutMillis);
    }

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
    }
}