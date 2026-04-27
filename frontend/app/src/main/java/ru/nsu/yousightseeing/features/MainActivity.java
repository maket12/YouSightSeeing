package ru.nsu.yousightseeing.features;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.yandex.mapkit.MapKitFactory;
import javax.inject.Inject;
import ru.nsu.yousightseeing.R;
import ru.nsu.yousightseeing.features.main.MainContract;
import ru.nsu.yousightseeing.features.main.MainUIManager;

public class MainActivity extends AppCompatActivity implements MainContract.View {

    @Inject
    MainUIManager uiManager;
    @Inject
    MainContract.Presenter presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DaggerMainComponent.builder()
                .mainModule(new MainModule(this))
                .build()
                .inject(this);

        boolean allowGeo = getIntent().getBooleanExtra("ALLOW_GEO", false);
        presenter.initialize(allowGeo);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        presenter.onRequestPermissionsResult(requestCode, grantResults);
    }

    @Override
    protected void onStart() {
        super.onStart();
        MapKitFactory.getInstance().onStart();
        if (uiManager.mapView != null) {
            uiManager.mapView.onStart();
        }
        presenter.onStart();
    }

    @Override
    protected void onStop() {
        if (uiManager.mapView != null) {
            uiManager.mapView.onStop();
        }
        MapKitFactory.getInstance().onStop();
        presenter.onStop();
        super.onStop();
    }

    @Override
    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void startActivity(Class<?> cls) {
        startActivity(new Intent(this, cls));
    }
}
