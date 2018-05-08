

#SeaDAS-OCSSW Client Server Configuration

##Introduction

The SeaDAS processing components (aka OCSSW) can only be installed on Linux or MacOSX (Intel, not PowerPC) systems. For this reason,
it has been difficult for Windows users to use OCSSW in an efficient way. SeaDAS 7.5 introduces the OCSSW server, which enables 
the pyhsical decoupling of the SeaDAS GUI application and the OCSSW package.


## OCSSW Server Configuration

The OCSSW server provides access to OCSSW programs through web services. 
 The web services are packaged as an independent jar file and it can be deployed
 on a machine capable of running OCSSW programs. With this new capacity,
  we have three additional settings that enable SeaDAS access to OCSSW programs. 
  
  One way is to install a Linux virtual machine on a VirtualBox on the same Windows machine that has SeaDAS installed. Another way is to use
  the Windows Subsystem for Linux. Finally, the web services package can be deployed on any Linux machine that provides network access to other
  computers. In this manual, we explain how to configure the OCSSW server and the SeaDAS client in each setting.
  
 
## 1. Installing an OCSSW Server on a Guest Virtual Machine


### Creating and Preparing a Virtual Machine
1. Install Oracle Virtualbox from https://www.virtualbox.org/ 

2. Create a Linux Ubuntu guest virtual machine. For SeaDAS 7.5, we used Ubuntu 16.04 LTS.

3. Set up VirtualBox Guest Additions on gui-less VM server
   
   $ sudo mount /dev/cdrom /media/cdrom.
   
   $ cd /media/cdrom.
   
   $ sudo apt-get install -y dkms build-essential linux-headers-generic linux-headers-$(uname -r).
   
   $ sudo su.
   
   $ ./VBoxLinuxAdditions.run
   
   $ reboot
   
   $ lsmod | grep -io vboxguest
   
   $ sudo usermod -aG vboxsf <user>
   
4. Set up Java
   
   $ sudo add-apt-repository ppa:webupd8team/java
   
   $ sudo apt update; sudo apt install oracle-java8-installer
   
   $ sudo apt install oracle-java8-set-default
   
5. Install python2.7

   $ sudo apt install python
   
   
6. Install Git

   $ sudo apt install git


###  Configuring the Virtual Machine

Since the SeaDAS client and the OCSSW server reside on two seperate machines, they  need to
share files and folders over a network, in other words, there is a necessity to access remote files.
For virtual machines, the network between host and guest is virtual since they are on the same real machine. By sharing folders, we eliminate 
the need for file transfer over the network.

###### Before sharing folders, you must install Guest Additions. 

#### Creating a shared folder

1. Create a folder on the Host computer (Windows) that you would like to share, for example C:\Users\${username}\seadasClientServerShared

2. Boot the Guest operating system in VirtualBox.

3. Create a folder on the Guest computer (Ubuntu) that you would like to share, for example ~/seadasClientServerShared

4. Select Devices -> Shared Folders...

5. Choose the 'Add' button.

6. Select C:\Users\${username}\seadasClientServerShared in the "Folder Path" and named it, for example, "seadasClientServerShared", in the "Folder Name".

7. Optionally select the 'Make permanent' option

####  Prepare the folder

With a shared folder named "seadasClientServerShared", as above, the folder can be mounted as the directory ~/seadasClientServerShared with the command

sudo mount -t vboxsf -o uid=$UID,gid=$(id -g) ocssw_shared ~/seadasClientServerShared

If you experience any problem with setting up a shared folder, please visit https://help.ubuntu.com/community/VirtualBox/SharedFolders.

###  Deploying the OCSSW Web Services Package

The OCSSW Web Services Package consisted of a jar file (seadas-ocsswserver-jar-with-dependencies.jar) and 
a configuration file (ocsswserver.config). They can be downloaded from https://seadas.gsfc.nasa.gov/downloads/ into a designated folder on
the virtual machine.

While the jar file contains a self-contained webserver and multiple 
web services for accessing various OCSSW programs, the configuration file provides crucial information for 
establishing successful communication between the OCSSW server and the SeaDAS client.
 
Here is the content of the config file:

    ocsswrest.version=1.0
    baseUriPortNumber=6400
    ocsswroot=${user.home}/ocssw
    serverWorkingDirectory=${user.home}/seadasClientServerShared
    clientServerSharedDir=true
    keepIntermediateFilesOnServer=false
    processInputStreamPortNumber=6402
    processErrorStreamPortNumber=6403

The config file properties related to port numbers and the folders are essential and should be set correctly matching the values in the client
configuration.  Please see the "SeaDAS Client Configuration" section of this document for the corresponding 
client configuration properties and their values.


###  Starting and Stopping the OCSSW Server

To start the OCSSW server, execute the following command:

      java -Xmx4G -jar seadas-ocsswserver-jar-with-dendencies.jar ocsswserver.config 

To stop the OCSSW server, press 'Ctrl' + 'C'.

## 2. Installing an OCSSW Server on a Windows Subsystem Linux

## 3. Installing an OCSSW Server on a Remote Machine

## SeaDAS Client Configuration

On the client side, there are two tasks that are instrumental in establishing successful communication 
 between the SeaDAS client and the OCSSW server.

1. Creating a shared folder, which is already covered above. In our example, we created a directory C:\Users\${username}\seadasClientServerShared 
to be shared with the OCSSW Server residing on the virtual machine.

2. Edit the following lines in the seadas.config file:

       seadas.ocssw.location = virtualMachine
       seadas.ocssw.port=6400
       seadas.ocssw.sharedDir=${user.dir}/seadasClientServerShared   # this name has to match the folder name actually shared with the virtual machine.
       seadas.client.id=${usreName}  #this is optional
       seadas.ocssw.keepFilesOnServer=false
       seadas.ocssw.processInputStreamPort=6402
       seadas.ocssw.processErrorStreamPort=6403
