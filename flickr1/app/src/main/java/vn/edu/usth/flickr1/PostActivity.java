package vn.edu.usth.flickr1;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;
import com.google.firebase.storage.UploadTask;

import java.util.HashMap;

public class PostActivity extends AppCompatActivity {
    Uri imageUri;
    String myUrl = "";
    StorageTask uploadTask;
    StorageReference storageReference;

    ImageView close, image_added;
    TextView post;
    EditText description;
    private CropImageOptions options;

    private final ActivityResultLauncher<CropImageContractOptions> cropImageLauncher =
            registerForActivityResult(new CropImageContract(), result -> {
                if (result.isSuccessful() && result.getUriContent() != null) {
                    imageUri = result.getUriContent();
                    image_added.setImageURI(imageUri);  // Display the cropped image
                } else {
                    Toast.makeText(PostActivity.this, "Image cropping failed!", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        cropImageLauncher.launch(new CropImageContractOptions(selectedImageUri, options));
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post);

        close = findViewById(R.id.close);
        image_added = findViewById(R.id.image_added);
        post = findViewById(R.id.post);
        description = findViewById(R.id.description);

        storageReference = FirebaseStorage.getInstance().getReference("posts");

        close.setOnClickListener(view -> {
            startActivity(new Intent(PostActivity.this, MainActivity.class));
            finish();
        });

        post.setOnClickListener(view -> {
            if (imageUri != null) {
                uploadImage();
            } else {
                Toast.makeText(PostActivity.this, "No Image Selected!", Toast.LENGTH_SHORT).show();
            }
        });

        options = new CropImageOptions();
        options.guidelines = CropImageView.Guidelines.ON;
        options.aspectRatioX = 1;
        options.aspectRatioY = 1;
        options.fixAspectRatio = true;

        image_added.setOnClickListener(view -> openGallery());
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    private String getFileExtension(Uri uri) {
        ContentResolver contentResolver = getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        String extension = mime.getExtensionFromMimeType(contentResolver.getType(uri));

        if (extension == null) {
            Log.e("UploadImage", "Failed to get file extension");
        } else {
            Log.d("UploadImage", "File extension: " + extension);
        }
        return extension;
    }

    private void uploadImage() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Posting...");
        progressDialog.show();

        if (imageUri != null) {
            Log.d("UploadImage", "Image URI for upload: " + imageUri.toString());

            String extension = getFileExtension(imageUri);
            if (extension == null) {
                progressDialog.dismiss();
                Toast.makeText(this, "Failed to determine file type!", Toast.LENGTH_SHORT).show();
                return;
            }

            StorageReference fileReference = storageReference.child(System.currentTimeMillis() + "." + extension);

            uploadTask = fileReference.putFile(imageUri);
            uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                @Override
                public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                    if (!task.isSuccessful()) {
                        Log.e("UploadImage", "Upload failed", task.getException());
                        throw task.getException();
                    }
                    return fileReference.getDownloadUrl();
                }
            }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                @Override
                public void onComplete(@NonNull Task<Uri> task) {
                    if (task.isSuccessful()) {
                        Uri downloadUri = task.getResult();
                        myUrl = downloadUri.toString();
                        Log.d("UploadImage", "Download URL: " + myUrl);

                        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("Posts");
                        String postId = reference.push().getKey();

                        HashMap<String, Object> hashMap = new HashMap<>();
                        hashMap.put("postid", postId);
                        hashMap.put("postimage", myUrl);
                        hashMap.put("description", description.getText().toString());
                        hashMap.put("publisher", FirebaseAuth.getInstance().getCurrentUser().getUid());

                        reference.child(postId).setValue(hashMap).addOnCompleteListener(task1 -> {
                            progressDialog.dismiss();
                            if (task1.isSuccessful()) {
                                startActivity(new Intent(PostActivity.this, MainActivity.class));
                                finish();
                            } else {
                                Log.e("UploadImage", "Failed to save post details");
                                Toast.makeText(PostActivity.this, "Failed to save post details!", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        progressDialog.dismiss();
                        Log.e("UploadImage", "Failed to retrieve download URL");
                        Toast.makeText(PostActivity.this, "Failed to upload image!", Toast.LENGTH_SHORT).show();
                    }
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    progressDialog.dismiss();
                    Log.e("UploadImage", "Upload failed with exception: " + e.getMessage());
                    Toast.makeText(PostActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            progressDialog.dismiss();
            Log.e("UploadImage", "No Image Selected!");
            Toast.makeText(this, "No Image Selected!", Toast.LENGTH_SHORT).show();
        }
    }
}
