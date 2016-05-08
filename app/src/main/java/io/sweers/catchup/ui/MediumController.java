package io.sweers.catchup.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.view.ContextThemeWrapper;
import android.util.Pair;
import android.view.View;

import com.squareup.moshi.Moshi;

import org.threeten.bp.Instant;

import java.util.List;

import javax.inject.Inject;

import dagger.Lazy;
import dagger.Provides;
import io.sweers.catchup.R;
import io.sweers.catchup.data.EpochInstantJsonAdapter;
import io.sweers.catchup.data.LinkManager;
import io.sweers.catchup.data.medium.MediumService;
import io.sweers.catchup.data.medium.model.Collection;
import io.sweers.catchup.data.medium.model.MediumPost;
import io.sweers.catchup.data.medium.model.MediumResponse;
import io.sweers.catchup.data.medium.model.Payload;
import io.sweers.catchup.injection.ForApi;
import io.sweers.catchup.injection.PerController;
import io.sweers.catchup.ui.activity.ActivityComponent;
import io.sweers.catchup.ui.activity.MainActivity;
import io.sweers.catchup.ui.base.BaseNewsController;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSource;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;
import rx.Observable;


public final class MediumController extends BaseNewsController<MediumPost> {

  @Inject MediumService service;
  @Inject LinkManager linkManager;

  public MediumController() {
    this(null);
  }

  public MediumController(Bundle args) {
    super(args);
  }

  @Override protected void performInjection() {
    DaggerMediumController_Component
        .builder()
        .module(new Module())
        .activityComponent(((MainActivity) getActivity()).getComponent())
        .build()
        .inject(this);
  }

  @Override protected Context onThemeContext(@NonNull Context context) {
    return new ContextThemeWrapper(context, R.style.CatchUp_Medium);
  }

  @Override
  protected void bindItemView(@NonNull BaseNewsController<MediumPost>.ViewHolder holder, @NonNull View view, @NonNull MediumPost item) {
    holder.title(item.post().title());

    holder.score(Pair.create("♥", item.post().virtuals().recommends()));
    holder.timestamp(item.post().createdAt());

    holder.author(item.user().name());

    Collection collection = item.collection();
    if (collection != null) {
      holder.source(collection.name());
    } else {
      holder.source(null);
    }

    holder.comments(item.post().virtuals().responsesCreatedCount());
  }

  @Override
  protected void onItemClick(@NonNull BaseNewsController<MediumPost>.ViewHolder holder, @NonNull View view, @NonNull MediumPost item) {
    linkManager.openUrl(item.constructUrl());
  }

  @Override
  protected void onCommentClick(@NonNull BaseNewsController<MediumPost>.ViewHolder holder, @NonNull View view, @NonNull MediumPost item) {
    linkManager.openUrl(item.constructUrl());
  }

  @NonNull @Override protected Observable<List<MediumPost>> getDataObservable() {
    return service.top()
        .map(MediumResponse::payload)
        .map(Payload::references)
        // TODO why doesn't this work? Only emits once
//        .flatMap(references -> {
//          return Observable.combineLatest(
//              Observable.from(references.Post().values()),
//              Observable.just(references.User()),
//              Observable.just(references.Collection()),
//              (post, userMap, collectionMap) -> {
//                return MediumPost.builder()
//                    .post(post)
//                    .user(userMap.get(post.creatorId()))
//                    .collection(collectionMap.get(post.homeCollectionId()))
//                    .build();
//              }
//          );
//        })
        .flatMap(references -> Observable.from(references.Post().values())
            .map(post -> MediumPost.builder()
                .post(post)
                .user(references.User().get(post.creatorId()))
                .collection(references.Collection().get(post.homeCollectionId()))
                .build()))
        .toList();
  }

  @PerController
  @dagger.Component(
      modules = Module.class,
      dependencies = ActivityComponent.class
  )
  public interface Component {
    void inject(MediumController controller);
  }

  @dagger.Module
  public static class Module {

    @Provides
    @PerController
    @ForApi
    OkHttpClient provideMediumOkHttpClient(OkHttpClient client) {
      return client.newBuilder()
          .addInterceptor(chain -> {
            Request request1 = chain.request();
            Response response = chain.proceed(request1);
            BufferedSource source = response.body().source();
            source.skip(source.indexOf((byte) '{'));
            return response;
          })
          .build();
    }

    @Provides
    @PerController
    @ForApi
    Moshi provideMediumMoshi(Moshi moshi) {
      return moshi.newBuilder()
          .add(Instant.class, new EpochInstantJsonAdapter())
          .build();
    }

    @Provides
    @PerController
    MediumService provideMediumService(
        @ForApi final Lazy<OkHttpClient> client,
        @ForApi Moshi moshi,
        RxJavaCallAdapterFactory rxJavaCallAdapterFactory) {
      Retrofit retrofit = new Retrofit.Builder()
          .baseUrl(MediumService.ENDPOINT)
          .callFactory(request -> client.get().newCall(request))
          .addCallAdapterFactory(rxJavaCallAdapterFactory)
          .addConverterFactory(MoshiConverterFactory.create(moshi))
          .build();
      return retrofit.create(MediumService.class);
    }
  }
}
