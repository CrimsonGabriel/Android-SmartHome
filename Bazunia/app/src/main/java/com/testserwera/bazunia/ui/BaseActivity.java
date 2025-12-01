package com.testserwera.bazunia.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.testserwera.bazunia.utils.AppearanceManager;
import com.testserwera.bazunia.utils.LocaleManager;
import com.testserwera.bazunia.R;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        AppearanceManager appearanceManager = new AppearanceManager(newBase);
        LocaleManager localeManager = new LocaleManager(newBase);
        Context contextWithLocale = localeManager.setLocale(newBase);
        Context finalContext = applyAppScale(contextWithLocale, appearanceManager);

        super.attachBaseContext(finalContext);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppearanceManager appearanceManager = new AppearanceManager(this);
        appearanceManager.applyThemeMode();
        setTheme(getButtonSizeThemeResId(appearanceManager.getButtonScale()));

        super.onCreate(savedInstanceState);
    }
    private Context applyAppScale(Context context, AppearanceManager appearanceManager) {
        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        float fontScale;
        switch (appearanceManager.getTextScale()) {
            case AppearanceManager.SCALE_SMALL: fontScale = 0.85f; break;
            case AppearanceManager.SCALE_LARGE: fontScale = 1.15f; break;
            default: fontScale = 1.0f;
        }
        config.fontScale = fontScale;

        return context.createConfigurationContext(config);
    }

    private int getButtonSizeThemeResId(String scale) {
        switch (scale) {
            case AppearanceManager.SCALE_SMALL: return R.style.Theme_Bazunia_ButtonSize_Small;
            case AppearanceManager.SCALE_LARGE: return R.style.Theme_Bazunia_ButtonSize_Large;
            default: return R.style.Theme_Bazunia_ButtonSize_Medium;
        }
    }
}