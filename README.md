# WorkflowTriggers
Workflow Triggers specific for Cascade on Florida Department of Health.  
## Setup
### Java
The Java version should match the current version running on Cascade in order to ensure there are no discrepancies between the two.  Currently, Cascade uses the Java 8 library.  Although you can get by with simple the JRE, it is preferred that
you get the SDK.  You can acquire the [Oracle Version](https://www.oracle.com/technetwork/java/javase/downloads/index.html) or the [Open SDK](http://openjdk.java.net/install/index.html) version.
### Eclipse
The user will need to download and set up Eclipse.  The basic installer can be found on the [Eclipse Website](https://www.eclipse.org/downloads/). Make sure to install the JEE version of Eclipse as this will give you fewer issues in terms of setup.
### Cascade
Because the Workflow code uses a library that is not part of the API Jars but only can be obtained from the expanded version of Cascade itself, you will need to copy the code directly into your Eclipse workspace.  You can then set up the build environment
to pull it in as another project.


