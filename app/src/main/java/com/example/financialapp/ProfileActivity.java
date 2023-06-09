package com.example.financialapp;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.financialapp.MainActivityPackage.MainAccountFragment;
import com.example.financialapp.Model.AccountModel;
import com.example.financialapp.Model.UserModel;
import com.example.financialapp.databinding.ActivityProfileBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.mozilla.javascript.tools.jsc.Main;

import cn.pedant.SweetAlert.SweetAlertDialog;

public class ProfileActivity extends AppCompatActivity {
    String allCountryRegex = "^(\\+\\d{1,3}( )?)?((\\(\\d{1,3}\\))|\\d{1,3})[- .]?\\d{3,4}[- .]?\\d{4}$";
    ActivityProfileBinding binding;
    UserModel tempUser;
    FirebaseStorage storage;
    GoogleSignInOptions gso;
    GoogleSignInClient gsc;
    SweetAlertDialog sweetAlertDialog;
    Uri tempImage;
    ActivityResultLauncher<String> getImage = registerForActivityResult(new ActivityResultContracts.GetContent(),
            new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri result) {
                    if (result != null) {
                        binding.imageProfile.setImageURI(result);
                        tempImage = result;
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setTitle(R.string.profileTT);

        sweetAlertDialog = new SweetAlertDialog(ProfileActivity.this, SweetAlertDialog.PROGRESS_TYPE);
        sweetAlertDialog.getProgressHelper().setBarColor(Color.parseColor("#A5DC86"));
        sweetAlertDialog.setCancelable(false);

        gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(com.firebase.ui.auth.R.string.default_web_client_id))
                .requestEmail()
                .requestProfile()
                .build();
        gsc = GoogleSignIn.getClient(this, gso);

        storage = FirebaseStorage.getInstance();

        tempUser = MainActivity.currentUser;

        binding.userName.setText(tempUser.getName());
        binding.userNumber.setText(tempUser.getNumber());
        binding.userEmail.setText(tempUser.getEmail());
        binding.imageProfile.setImageResource(R.drawable.default_profile_picture);

        if (MainActivity.profilePicture != null) {
            Glide.with(getApplicationContext())
                    .load(MainActivity.profilePicture)
                    .into(binding.imageProfile);
        }

        binding.logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FirebaseFirestore.getInstance().collection("User").document(MainActivity.currentUser.getId())
                        .update("signIn", false)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unused) {
                                gsc.signOut();
                                FirebaseAuth.getInstance().signOut();
                                MainActivity.profilePicture = null;
                                finishAffinity();
                                finishAndRemoveTask();
                                MainAccountFragment.currentAccId = "";
                                startActivity(new Intent(ProfileActivity.this, LoginActivity.class));
                                finish();
                            }
                        });
            }
        });

        binding.changePasswordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sweetAlertDialog = new SweetAlertDialog(ProfileActivity.this, SweetAlertDialog.PROGRESS_TYPE);
                sweetAlertDialog.getProgressHelper().setBarColor(Color.parseColor("#A5DC86"));
                sweetAlertDialog.setCancelable(false);
                sweetAlertDialog.show();
                updatePassword();
            }
        });

        binding.imageProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getImage.launch("image/*");
            }
        });

        binding.deleteUserTV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sweetAlertDialog = new SweetAlertDialog(ProfileActivity.this, SweetAlertDialog.WARNING_TYPE);
                String confirmDelete = getResources().getString(R.string.confirm_deleteTT);
                String confirmDeleteContent = getResources().getString(R.string.confirm_deleteContent);
                String confirmDeleteText = getResources().getString(R.string.confirm_deleteText);
                sweetAlertDialog.setTitleText(confirmDelete);
                sweetAlertDialog.setContentText(confirmDeleteContent);
                sweetAlertDialog.setConfirmText(confirmDeleteText);
                sweetAlertDialog.setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                    @Override
                    public void onClick(SweetAlertDialog sweetAlertDialog) {
                        sweetAlertDialog.dismissWithAnimation();
                        deleteUser();
                    }
                });
                sweetAlertDialog.show();
            }
        });

        setContentView(binding.getRoot());
    }

    private void deleteUser() {
        sweetAlertDialog = new SweetAlertDialog(ProfileActivity.this, SweetAlertDialog.PROGRESS_TYPE);
        sweetAlertDialog.getProgressHelper().setBarColor(Color.parseColor("#A5DC86"));
        sweetAlertDialog.setCancelable(false);
        sweetAlertDialog.show();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        deleteAllUserData();
        assert user != null;
        user.delete().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                gsc.signOut();
                FirebaseAuth.getInstance().signOut();
                MainActivity.profilePicture = null;
                finishAffinity();
                finishAndRemoveTask();
                MainAccountFragment.currentAccId = "";
                sweetAlertDialog.dismissWithAnimation();
                startActivity(new Intent(ProfileActivity.this, LoginActivity.class));
                finish();
            }
        });
    }

    private void deleteAllUserData() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("User").document(tempUser.getId()).delete()
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            db.collection("Account").whereEqualTo("userId", tempUser.getId()).get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                @Override
                                public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                    if (task.isSuccessful()) {
                                        for (QueryDocumentSnapshot documentSnapshot : task.getResult()) {
                                            AccountModel tempAccountModel = documentSnapshot.toObject(AccountModel.class);
                                            db.collection("Account").document(documentSnapshot.getId()).delete().addOnCompleteListener(new OnCompleteListener<Void>() {
                                                @Override
                                                public void onComplete(@NonNull Task<Void> task) {
                                                    if (task.isSuccessful()) {
                                                        db.collection("Transaction").whereEqualTo("accountId", tempAccountModel.getId()).get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                                            @Override
                                                            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                                                if (task.isSuccessful()) {
                                                                    int cnt = 0;
                                                                    for (QueryDocumentSnapshot documentSnapshot : task.getResult()) {
                                                                        db.collection("Transaction").document(documentSnapshot.getId()).delete();
                                                                    }
                                                                }
                                                            }
                                                        });
                                                    }
                                                }
                                            });
                                        }
                                    }
                                }
                            });

                            db.collection("Goal").whereEqualTo("userId", tempUser.getId()).get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                @Override
                                public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                    if (task.isSuccessful()) {
                                        for (QueryDocumentSnapshot documentSnapshot : task.getResult()) {
                                            db.collection("Goal").document(documentSnapshot.getId()).delete();
                                        }
                                    }
                                }
                            });

                            db.collection("Budget").whereEqualTo("userId", tempUser.getId()).get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                @Override
                                public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                    if (task.isSuccessful()) {
                                        for (QueryDocumentSnapshot documentSnapshot : task.getResult()) {
                                            db.collection("Budget").document(documentSnapshot.getId()).delete();
                                        }
                                    }
                                }
                            });

                            StorageReference reference = storage.getReference().child("images/" + tempUser.getId());
                            reference.delete();
                        }
                    }
                });
    }

    private void updatePassword() {
        String password = binding.changedPassword.getText().toString();
        String cf_password = binding.changedPasswordCf.getText().toString();
        if (password.length() == 0) {
            sweetAlertDialog.dismissWithAnimation();
            binding.changedPassword.setError("Empty");
            return;
        }
        if (cf_password.length() == 0) {
            sweetAlertDialog.dismissWithAnimation();
            binding.changedPasswordCf.setError("Empty");
            return;
        }
        if (!password.equals(cf_password)) {
            sweetAlertDialog.dismissWithAnimation();
            binding.changedPasswordCf.setError("Password does not match!");
            return;
        }
        FirebaseAuth.getInstance().getCurrentUser().updatePassword(password)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Toast.makeText(ProfileActivity.this, "Password update!", Toast.LENGTH_SHORT).show();
                        sweetAlertDialog.dismissWithAnimation();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(ProfileActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                        sweetAlertDialog.dismissWithAnimation();
                    }
                });
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.profile_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (R.id.saveChanges_button == id) {
            sweetAlertDialog = new SweetAlertDialog(ProfileActivity.this, SweetAlertDialog.PROGRESS_TYPE);
            sweetAlertDialog.getProgressHelper().setBarColor(Color.parseColor("#A5DC86"));
            sweetAlertDialog.setCancelable(false);
            sweetAlertDialog.show();
            String name = binding.userName.getText().toString();
            String number = binding.userNumber.getText().toString();
            if (name.length() == 0) {
                binding.userName.setError("Empty!");
                sweetAlertDialog.dismissWithAnimation();
                return false;
            }
            if (!number.matches(allCountryRegex)) {
                binding.userNumber.setError("Badly formatted!");
                sweetAlertDialog.dismissWithAnimation();
                return false;
            }
            tempUser.setName(name);
            tempUser.setNumber(number);
            FirebaseFirestore.getInstance().collection("User").document(tempUser.getId())
                    .set(tempUser)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            if (tempImage != null) {
                                StorageReference reference = storage.getReference().child("images/" + tempUser.getId());
                                reference.putFile(tempImage).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                                    @Override
                                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                        Log.d("FinancialApp", "Upload profile picture successfully!");
                                        Toast.makeText(ProfileActivity.this, "Update successfully!", Toast.LENGTH_SHORT).show();
                                        sweetAlertDialog.dismissWithAnimation();
                                        startActivity(new Intent(ProfileActivity.this, MainActivity.class));
                                        finish();
                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.d("FinancialApp", "Upload profile picture failed!");
                                        Toast.makeText(ProfileActivity.this, "Update successfully!", Toast.LENGTH_SHORT).show();
                                        sweetAlertDialog.dismissWithAnimation();
                                        startActivity(new Intent(ProfileActivity.this, MainActivity.class));
                                        finish();
                                    }
                                });
                            } else {
                                sweetAlertDialog.dismissWithAnimation();
                                Toast.makeText(ProfileActivity.this, "Update successfully!", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(ProfileActivity.this, MainActivity.class));
                                finish();
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            sweetAlertDialog.dismissWithAnimation();
                            Toast.makeText(ProfileActivity.this, "Update failed!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(ProfileActivity.this, MainActivity.class));
                            finish();
                        }
                    });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}