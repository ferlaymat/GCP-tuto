# GCP-tuto

To start with Google Cloud Platform as cheaply as possible, you have 2 possibilities:

- Using the free 300$ for 90 days since your subscription.
- Using Always Free tier, which are permanent free or almost free services.
  The next array is a short recap of main free tiers. You can have more info here: [Always free tier](https://cloud.google.com/free).

| Service         | Free or limited                                 |
| --------------- |-------------------------------------------------|
| Compute Engine  | 1 VM e2-micro (us-east1, us-west1, us-central1) |
| Cloud Storage   | 5GB (us region only)                            |
| Big Query       | 1TB of queries/month and 10 Go storage          |
| Cloud Functions | 2M calls/month                                  |
| Firestore       | 1GB,50k read/day,20k write/day,20k delete/day   |
| Secret Manager  | 6 versions of active secrets, 10k access/month  |
| Cloud Shell     | Unlimited access to the terminal                |

In this repository, we will see how to create a VM and initialize it with the Cloud Shell and with a Java app. It exists many languages supported by Google to do it, feel free to use your favorite one.
The first step is to create a GCP account and then, to install the SDK by following this doc: [https://docs.cloud.google.com/sdk/docs](https://docs.cloud.google.com/sdk/docs). You should be able to log in to the platform as in the Cloud Shell, but from your laptop.

## Preparation step
Before starting to create a VM, the first service that you have to know is the IAM service. Why? Because of questions of security access. You should never use your main admin account to manage daily operations. In case of credentialcompromise, you could face serious financial problems by over consuming unwanted resources.
Keep in mind that you'll pay for what you use.
Thus, a good practice is to provide a service account for each developer to reduce their access (even for yourself).

To do that, connect to your platform and open the Cloud Shell by clicking on the button '>_' on the right of the header menu.

Enter following commands:

- Create a service account

```bash
gcloud iam service-accounts create java-vm-manager
```

- Fetch the service account id(email).It should have the format java-vm-manager@YOUR_PROJECT_ID.iam.gserviceaccount.com:

```bash
gcloud iam service-accounts list
```

- Link it with only needed rights (replace values by yours). 

```bash
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:YOUR_NEW_SERVICE_ACCOUNT_ID" \
  --role="roles/compute.instanceAdmin.v1"

gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:YOUR_NEW_SERVICE_ACCOUNT_ID" \
  --role="roles/compute.securityAdmin"
```

- Generate a key for the auto login

```bash
gcloud iam service-accounts keys create ~/java-key.json --iam-account=YOUR_NEW_SERVICE_ACCOUNT_ID
```

- Copy the content of the key in your laptop and set the path to the environment variable GOOGLE_APPLICATION_CREDENTIALS. This will allow your app to connect to your GCP project.

- Revoke the current connection to be sure to not use your account.

```bash
gcloud auth application-default revoke
```

- Activate your service account key to let you use it for the authentication. Take care to add enough rights for what you have to do or create another service account.

```bash
gcloud auth activate-service-account --key-file=/path/to/your/key.json
```

## Execution step
The following part will show you how to create and manipulate a VM instance in GCP. Following commands and Java app do exactly the same actions. If you had followed the preparation step, you should be able to use
those commands in Cloud Shell and in your laptop.
- For the Java mode, just open the project in your favorite IDE and launch it.
- For the Cloud Shell mode, use following commands:

- Check the active configuration
```bash
gcloud config list
```

- Create a VM instance
```bash
gcloud compute instances create java-managed-vm \
  --machine-type=e2-micro \
  --zone=us-east1-b \
  --image-family=debian-12 \
  --image-project=debian-cloud \
  --tags=web
```

- Connect to your instance via ssh
```bash
gcloud compute ssh java-managed-vm --zone=us-east1-b
```

- Install nginx and exit
```bash
sudo apt update && sudo apt install -y nginx
sudo systemctl status nginx
exit
```

- Create a firewall rule to allow HTTP connection
```bash
gcloud compute firewall-rules create allow-http \
  --allow=tcp:80 \
  --target-tags=web \
  --description="Allow HTTP traffic"
```

- Get the external IP and do a curl call to test the connection
```bash
gcloud compute instances describe java-managed-vm \
  --zone=us-east1-b \
  --format="get(networkInterfaces[0].accessConfigs[0].natIP)"

curl http://EXTERNAL_IP
```
- Delete the firewall rule
```bash
gcloud compute firewall-rules delete allow-http --quiet
```

- Stop, start and delete your instance
```bash
gcloud compute instances stop java-managed-vm --zone=us-east1-b

gcloud compute instances start java-managed-vm --zone=us-east1-b

gcloud compute instances delete java-managed-vm --zone=us-east1-b
```



