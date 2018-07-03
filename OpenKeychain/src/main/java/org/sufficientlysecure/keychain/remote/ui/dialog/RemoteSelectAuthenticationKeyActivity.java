/*
 * Copyright (C) 2017 Schürmann & Breitmoser GbR
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.remote.ui.dialog;


import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.text.format.DateUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.mikepenz.materialdrawer.util.KeyboardUtil;
import org.openintents.ssh.authentication.SshAuthenticationApi;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.livedata.GenericLiveData;
import org.sufficientlysecure.keychain.model.SubKey.UnifiedKeyInfo;
import org.sufficientlysecure.keychain.daos.ApiAppDao;
import org.sufficientlysecure.keychain.daos.KeyRepository;
import org.sufficientlysecure.keychain.remote.ui.RemoteSecurityTokenOperationActivity;
import org.sufficientlysecure.keychain.remote.ui.dialog.RemoteSelectAuthenticationKeyPresenter.RemoteSelectAuthenticationKeyView;
import org.sufficientlysecure.keychain.ui.dialog.CustomAlertDialogBuilder;
import org.sufficientlysecure.keychain.ui.util.KeyInfoFormatter;
import org.sufficientlysecure.keychain.ui.util.ThemeChanger;
import org.sufficientlysecure.keychain.ui.util.recyclerview.DividerItemDecoration;
import org.sufficientlysecure.keychain.ui.util.recyclerview.RecyclerItemClickListener;


public class RemoteSelectAuthenticationKeyActivity extends FragmentActivity {
    public static final String EXTRA_PACKAGE_NAME = "package_name";


    private RemoteSelectAuthenticationKeyPresenter presenter;
    private String packageName;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        presenter = new RemoteSelectAuthenticationKeyPresenter(getBaseContext(), this);

        KeyboardUtil.hideKeyboard(this);

        if (savedInstanceState == null) {
            RemoteSelectAuthenticationKeyDialogFragment frag = new RemoteSelectAuthenticationKeyDialogFragment();
            frag.show(getSupportFragmentManager(), "selectAuthenticationKeyDialog");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent intent = getIntent();
        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);

        SelectAuthKeyViewModel viewModel = ViewModelProviders.of(this).get(SelectAuthKeyViewModel.class);
        viewModel.setPackageName(packageName);

        presenter.setupFromViewModel(viewModel);
    }

    public static class SelectAuthKeyViewModel extends ViewModel {
        private LiveData<List<UnifiedKeyInfo>> keyInfoLiveData;
        private String packageName;

        public LiveData<List<UnifiedKeyInfo>> getKeyInfoLiveData(Context context) {
            if (keyInfoLiveData == null) {
                keyInfoLiveData = new GenericLiveData<>(context, () -> {
                    KeyRepository keyRepository = KeyRepository.create(context);
                    return keyRepository.getAllUnifiedKeyInfoWithSecret();
                });
            }
            return keyInfoLiveData;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public String getPackageName() {
            return packageName;
        }
    }

    private void onKeySelected(long masterKeyId) {
        Intent callingIntent = getIntent();
        Intent originalIntent = callingIntent.getParcelableExtra(
                RemoteSecurityTokenOperationActivity.EXTRA_DATA);

        ApiAppDao apiAppDao = ApiAppDao.getInstance(getBaseContext());
        apiAppDao.addAllowedKeyIdForApp(packageName, masterKeyId);

        originalIntent.putExtra(SshAuthenticationApi.EXTRA_KEY_ID, String.valueOf(masterKeyId));

        setResult(RESULT_OK, originalIntent);
        finish();
    }

    public static class RemoteSelectAuthenticationKeyDialogFragment extends DialogFragment {
        private RemoteSelectAuthenticationKeyPresenter presenter;
        private RemoteSelectAuthenticationKeyView mvpView;

        private Button buttonSelect;
        private Button buttonCancel;
        private RecyclerView keyChoiceList;

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Activity activity = requireActivity();

            ContextThemeWrapper theme = ThemeChanger.getDialogThemeWrapper(activity);
            CustomAlertDialogBuilder alert = new CustomAlertDialogBuilder(theme);

            LayoutInflater layoutInflater = LayoutInflater.from(theme);
            @SuppressLint("InflateParams")
            View view = layoutInflater.inflate(R.layout.api_remote_select_authentication_key, null, false);
            alert.setView(view);

            buttonSelect = view.findViewById(R.id.button_select);
            buttonCancel = view.findViewById(R.id.button_cancel);

            keyChoiceList = view.findViewById(R.id.authentication_key_list);
            keyChoiceList.setLayoutManager(new LinearLayoutManager(activity));
            keyChoiceList.addItemDecoration(
                    new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL_LIST, true));

            setupListenersForPresenter();
            mvpView = createMvpView(view, layoutInflater);

            return alert.create();
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            presenter = ((RemoteSelectAuthenticationKeyActivity) requireActivity()).presenter;
            presenter.setView(mvpView);
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);

            if (presenter != null) {
                presenter.onCancel();
            }
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);

            if (presenter != null) {
                presenter.setView(null);
                presenter = null;
            }
        }

        @NonNull
        private RemoteSelectAuthenticationKeyView createMvpView(View view, LayoutInflater layoutInflater) {
            final ImageView iconClientApp = view.findViewById(R.id.icon_client_app);
            final KeyChoiceAdapter keyChoiceAdapter = new KeyChoiceAdapter(layoutInflater, getResources());
            keyChoiceList.setAdapter(keyChoiceAdapter);

            return new RemoteSelectAuthenticationKeyView() {
                @Override
                public void finish(long masterKeyId) {
                    FragmentActivity activity = getActivity();
                    if (activity == null) {
                        return;
                    }

                    ((RemoteSelectAuthenticationKeyActivity)activity).onKeySelected(masterKeyId);
                }

                @Override
                public void finishAsCancelled() {
                    FragmentActivity activity = getActivity();
                    if (activity == null) {
                        return;
                    }

                    activity.setResult(RESULT_CANCELED);
                    activity.finish();
                }

                @Override
                public void setTitleClientIcon(Drawable drawable) {
                    iconClientApp.setImageDrawable(drawable);
                    keyChoiceAdapter.setSelectionDrawable(drawable);
                }

                @Override
                public void setKeyListData(List<UnifiedKeyInfo> data) {
                    keyChoiceAdapter.setData(data);
                }

                @Override
                public void setActiveItem(Integer position) {
                    keyChoiceAdapter.setActiveItem(position);
                }

                @Override
                public void setEnableSelectButton(boolean enabled) {
                    buttonSelect.setEnabled(enabled);
                }
            };
        }

        private void setupListenersForPresenter() {
            buttonSelect.setOnClickListener(view -> presenter.onClickSelect());
            buttonCancel.setOnClickListener(view -> presenter.onClickCancel());
            keyChoiceList.addOnItemTouchListener(new RecyclerItemClickListener(getContext(),
                    (view, position) -> presenter.onKeyItemClick(position)));
        }
    }

    private static class KeyChoiceAdapter extends Adapter<KeyChoiceViewHolder> {
        private final LayoutInflater layoutInflater;
        private final Resources resources;
        private List<UnifiedKeyInfo> data;
        private Drawable iconUnselected;
        private Drawable iconSelected;
        private Integer activeItem;

        KeyChoiceAdapter(LayoutInflater layoutInflater, Resources resources) {
            this.layoutInflater = layoutInflater;
            this.resources = resources;
        }

        @NonNull
        @Override
        public KeyChoiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View keyChoiceItemView = layoutInflater.inflate(R.layout.authentication_key_item, parent, false);
            return new KeyChoiceViewHolder(keyChoiceItemView);
        }

        @Override
        public void onBindViewHolder(@NonNull KeyChoiceViewHolder holder, int position) {
            UnifiedKeyInfo keyInfo = data.get(position);
            Drawable icon = (activeItem != null && position == activeItem) ? iconSelected : iconUnselected;
            holder.bind(keyInfo, icon);
        }

        @Override
        public int getItemCount() {
            return data != null ? data.size() : 0;
        }

        public void setData(List<UnifiedKeyInfo> data) {
            this.data = data;
            notifyDataSetChanged();
        }

        void setSelectionDrawable(Drawable drawable) {
            ConstantState constantState = drawable.getConstantState();
            if (constantState == null) {
                return;
            }

            iconSelected = constantState.newDrawable(resources);

            iconUnselected = constantState.newDrawable(resources);
            DrawableCompat.setTint(iconUnselected.mutate(), ResourcesCompat.getColor(resources, R.color.md_grey_300, null));

            notifyDataSetChanged();
        }

        void setActiveItem(Integer newActiveItem) {
            Integer prevActiveItem = this.activeItem;
            this.activeItem = newActiveItem;

            if (prevActiveItem != null) {
                notifyItemChanged(prevActiveItem);
            }
            if (newActiveItem != null) {
                notifyItemChanged(newActiveItem);
            }
        }
    }

    private static class KeyChoiceViewHolder extends RecyclerView.ViewHolder {
        private final TextView vName;
        private final TextView vCreation;
        private final ImageView vIcon;

        KeyChoiceViewHolder(View itemView) {
            super(itemView);

            vName = itemView.findViewById(R.id.key_list_item_name);
            vCreation = itemView.findViewById(R.id.key_list_item_creation);
            vIcon = itemView.findViewById(R.id.key_list_item_icon);
        }

        void bind(UnifiedKeyInfo keyInfo, Drawable selectionIcon) {
            vName.setText(keyInfo.name());

            KeyInfoFormatter keyInfoFormatter = new KeyInfoFormatter(itemView.getContext(), keyInfo, null);
            keyInfoFormatter.formatCreationDate(vCreation);

            vIcon.setImageDrawable(selectionIcon);
        }
    }

}
