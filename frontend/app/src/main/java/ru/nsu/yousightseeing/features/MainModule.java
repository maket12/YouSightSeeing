package ru.nsu.yousightseeing.features;

import dagger.Module;
import dagger.Provides;
import ru.nsu.yousightseeing.features.main.MainContract;
import ru.nsu.yousightseeing.features.main.MainPresenter;
import ru.nsu.yousightseeing.features.main.MainUIManager;

@Module
public class MainModule {

    private final MainActivity mainActivity;

    public MainModule(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    @Provides
    MainContract.View provideMainView() {
        return mainActivity;
    }

    @Provides
    MainUIManager provideMainUIManager() {
        return new MainUIManager(mainActivity);
    }

    @Provides
    MainContract.Presenter provideMainPresenter(MainUIManager uiManager) {
        return new MainPresenter(mainActivity, uiManager);
    }
}
