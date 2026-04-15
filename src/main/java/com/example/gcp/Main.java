package com.example.gcp;

import com.google.cloud.compute.v1.*;
import com.google.cloud.compute.v1.Firewall;
import com.google.cloud.compute.v1.Firewall.Direction;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Main {

    private static final String PROJECT_ID  = "***********"; //replace by your project id
    private static final String ZONE        = "us-east1-b";
    private static final String VM_NAME     = "java-managed-vm";
    private static final String MACHINE_TYPE = "e2-micro";
    private static final String NETWORK = "global/networks/default";
    private static final String FIREWALL_RULE_NAME = "allow-http";

    // Startup script injected a the VM start
    private static final String STARTUP_SCRIPT = """
            #!/bin/bash
            apt-get update
            apt-get install -y nginx
            systemctl start nginx
            echo "VM initialized at $(date)" > /var/log/startup-done.log
            """;

    public static void main(String[] args)
            throws IOException, ExecutionException, InterruptedException, TimeoutException {

        // GOOGLE_APPLICATION_CREDENTIALS are automatically loaded
        try (InstancesClient client = InstancesClient.create()) {

            System.out.println("=== Create a VM ===");
            createVm(client);

            System.out.println("\n=== List VMs to validate that the new one is here ===");
            listVms(client);

            System.out.println("\n=== Clean firewall rules ===");
            deleteFirewallRule();

            System.out.println("\n=== Create firewall rules ===");
            createFirewallRule();

            System.out.println("\n=== Call nginx inside the VM ===");
            testHttpConnection(client);

            System.out.println("\n=== Clean firewall rules to back to the initial state ===");
            deleteFirewallRule();

            System.out.println("\n=== Stop the VM ===");
            stopInstance(client);

            System.out.println("\n=== List VMs to validate to get the status ===");
            listVms(client);

            System.out.println("\n=== Start the VM ===");
            startInstance(client);

            System.out.println("\n=== List VMs to validate to get the status ===");
            listVms(client);

            System.out.println("\n=== Delete VM ===");
            deleteVm(client);

            System.out.println("\n=== List VMs  to validate that the new one has been deleted ===");
            listVms(client);
        }
    }


    static void createVm(InstancesClient client)
            throws ExecutionException, InterruptedException, TimeoutException {

        // Attach the mandatory boot disk
        AttachedDisk disk = AttachedDisk.newBuilder()
                .setBoot(true)// tell is a boot disk
                .setAutoDelete(true) //delete the disk during the vm deletion
                .setInitializeParams(AttachedDiskInitializeParams.newBuilder()
                        .setSourceImage("projects/debian-cloud/global/images/family/debian-12") // set the default os
                        .build())
                .build();

        // Link to the default network
        NetworkInterface networkInterface = NetworkInterface.newBuilder()
                .setNetwork(NETWORK)//select default network
                .addAccessConfigs(AccessConfig.newBuilder()
                        .setName("External NAT")
                        .setType(AccessConfig.Type.ONE_TO_ONE_NAT.toString())//provide external IP
                        .build())
                .build();

        // Startup script
        Items item = Items.newBuilder().setKey("startup-script").setValue(STARTUP_SCRIPT).build();
        Metadata metadata = Metadata.newBuilder()
                .addItems(item)
                .build();

        // VM definition
        Instance instance = Instance.newBuilder()
                .setName(VM_NAME)
                .setMachineType(
                        String.format("zones/%s/machineTypes/%s", ZONE, MACHINE_TYPE))
                .addDisks(disk)
                .addNetworkInterfaces(networkInterface)
                .setTags(Tags.newBuilder().addItems("web").build())  // Tag required for the firewall rule
                .setMetadata(metadata)
                .build();

        // Create the creation query
        InsertInstanceRequest request = InsertInstanceRequest.newBuilder()
                .setProject(PROJECT_ID)
                .setZone(ZONE)
                .setInstanceResource(instance)
                .build();

        System.out.println("Creation in progress...");
        //send the query
        client.insertAsync(request).get(3, TimeUnit.MINUTES);
        System.out.println("VM created : " + VM_NAME);
    }


    static void listVms(InstancesClient client) {

        ListInstancesRequest request = ListInstancesRequest.newBuilder()
                .setProject(PROJECT_ID)
                .setZone(ZONE)
                .build();

        InstancesClient.ListPagedResponse response = client.list(request);

        boolean found = false;
        for (Instance instance : response.iterateAll()) {
            System.out.printf("  - %-30s | %-10s | %s%n",
                    instance.getName(),
                    instance.getStatus(),
                    instance.getMachineType().substring(
                            instance.getMachineType().lastIndexOf("/") + 1));
            found = true;
        }
        if (!found) {
            System.out.println(" No Vm found.");
        }
    }


    static void stopInstance(InstancesClient client)
            throws ExecutionException, InterruptedException, TimeoutException {
        StopInstanceRequest stopInstanceRequest = StopInstanceRequest.newBuilder()
                .setProject(PROJECT_ID)
                .setZone(ZONE)
                .setInstance(VM_NAME)
                .build();
        System.out.println("Stop in progress...");
        client.stopAsync(stopInstanceRequest).get(3, TimeUnit.MINUTES);
        System.out.println("VM has been stopped : " + VM_NAME);
    }

    static void startInstance(InstancesClient client)
            throws ExecutionException, InterruptedException, TimeoutException {
        StartInstanceRequest startInstanceRequest = StartInstanceRequest.newBuilder()
                .setProject(PROJECT_ID)
                .setZone(ZONE)
                .setInstance(VM_NAME)
                .build();
        System.out.println("Start in progress...");
        client.startAsync(startInstanceRequest).get(3, TimeUnit.MINUTES);
        System.out.println("VM has been start : " + VM_NAME);
    }

    static void deleteVm(InstancesClient client)
            throws ExecutionException, InterruptedException, TimeoutException {

        DeleteInstanceRequest request = DeleteInstanceRequest.newBuilder()
                .setProject(PROJECT_ID)
                .setZone(ZONE)
                .setInstance(VM_NAME)
                .build();

        System.out.println("Deletion in progress...");
        client.deleteAsync(request).get(3, TimeUnit.MINUTES);
        System.out.println("VM has been deleted : " + VM_NAME);
    }


    static void deleteFirewallRule()
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        try (FirewallsClient client = FirewallsClient.create()) {


            try {
                client.get(PROJECT_ID, FIREWALL_RULE_NAME);
                System.out.println("Rule found: " + FIREWALL_RULE_NAME);

                DeleteFirewallRequest request = DeleteFirewallRequest.newBuilder()
                        .setProject(PROJECT_ID)
                        .setFirewall(FIREWALL_RULE_NAME)
                        .build();
                client.deleteAsync(request).get(3, TimeUnit.MINUTES);

            } catch (com.google.api.gax.rpc.NotFoundException e) {
                System.out.println("Rule not found : "
                        + FIREWALL_RULE_NAME);
            }
        }
    }

    static void createFirewallRule()
            throws IOException, ExecutionException, InterruptedException, TimeoutException {

        try (FirewallsClient firewallsClient = FirewallsClient.create()) {


            Firewall firewallRule = Firewall.newBuilder()
                    .setName(FIREWALL_RULE_NAME)
                    .setDirection(Direction.INGRESS.toString())
                    .addAllowed(
                            Allowed.newBuilder().addPorts("80").setIPProtocol("tcp").build())
                    .addSourceRanges("0.0.0.0/0")
                    .setNetwork(NETWORK)
                    .addTargetTags("web") //apply firewall rule only on VM instance with the tag "web"
                    .setDescription("Allowing TCP traffic on port 80from Internet.")
                    .build();


            InsertFirewallRequest insertFirewallRequest = InsertFirewallRequest.newBuilder()
                    .setFirewallResource(firewallRule)
                    .setProject(PROJECT_ID).build();

            firewallsClient.insertAsync(insertFirewallRequest).get(3, TimeUnit.MINUTES);

            System.out.println("Firewall rule created successfully -> " + FIREWALL_RULE_NAME);
        }
    }



    static void testHttpConnection(InstancesClient client)
            throws IOException, InterruptedException {

        Instance instance = client.get(PROJECT_ID, ZONE, VM_NAME);

        String externalIp =  instance.getNetworkInterfaces(0)
                .getAccessConfigs(0)
                .getNatIP();
        System.out.println("Connect to: " + externalIp);

        ProcessBuilder pb = new ProcessBuilder(
                "curl",
                "-s",           // silent mode
                "-m", "10",     // timeout 10 secondes
                "http://" + externalIp
        );

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            String output = new String(process.getInputStream().readAllBytes());
            System.out.println("Response:");
            System.out.println(output);
        } else {
            System.err.println("curl failed: " + exitCode);
        }
    }
}
