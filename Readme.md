# Production ready Kubectl plugins with GraalVM, Scala and Fabric8 Kubernetes client

## Motivation

Since a few months I started working in [Lightbend](https://www.lightbend.com/), and my firsts assignments have been around a little known and super-powerful product called [Cloudflow](https://cloudflow.io/).
TL;DR [Cloudflow](https://cloudflow.io/) is a set of libraries, tools, plugins to get you up-to-speed in developing Streaming applications on Kubernetes on top of popular streaming engines (Akka, Spark and Flink).

One of the strenght of [Cloudflow](https://cloudflow.io/) is it's CLI that comes in the form of a `kubectl plugin`.
In the CLI we perform an incredible amount of checks and validations that will prevent you from deploying an application not configured properly; and we automate a number of actions that will require specific and deep knowledge to be performed manually.

At the time I joined [Lightbend](https://www.lightbend.com/), the CLI used to be a classic Go application, organically grown up to the point technical debt prevented from easily adding functionalities and doing bug-fixes.
Another reason to reconsider that choice is the fact that support for Cloudflow's configuration format [HOCON](https://github.com/lightbend/config) is pretty poor in Go and caused a number of open issues not easly fixable.

We decided to entirely re-write the CLI with those principles:
 - programming language the team is comfortable with -> Scala
 - native performance -> GraalVM AOT
 - industry's standard libraries -> Fabric8 Kubernetes Client
 - standard Scala's stack -> HOCON/Scopt/Airframe logging/Pureconfig ...

## Give me the code!

The demo project we will refer to, is directly extracted from the Cloudflow's CLI, adapted to an example based this great article: [Kubectl's plugins in Java](https://dev.to/ikwattro/write-a-kubectl-plugin-in-java-with-jbang-and-fabric8-566) that happened to be published during our re-write.

The code is available here:
<TODO>

While original code of the Cloudflow CLI is open source here:
https://github.com/lightbend/cloudflow/tree/master/tools

You can generate the binaries(tested on MacOsX) running:
```
sbt graalvm-native-image:packageBin
```
and use them:
```
export PATH=$PATH:$PWD/target/graalvm-native-image
kubectl lp --help
kubectl lp version
kubectl lp list
...
```

## The stack

In this example we make use a few great libraries that, among others, have immensely helped this project to be successful.

 - [Scopt](https://github.com/scopt/scopt) for command line option parsing, it supports really well sub-commands and is a tiny lovely library.
 - [Airframe Log](https://github.com/wvlet/airframe/tree/master/airframe-log) for the "internal" logging.
 - [Fabric8 Kubernetes Client](https://github.com/fabric8io/kubernetes-client) an amazing project and, without it nothing would be possible, is a rock solid library that cover basically the entire Kubernetes API.

## GraalVM

We reserve a special section to [GraalVM](https://github.com/oracle/graal), and, for the purpose of this post, we refer exclusively at the Ahead Of Time compilation functionality.

GraalVM enabled us to compile Scala code with Scala and Java dependencies directly down to native binaries so that, the user, wouldn't incur into the JVM long start-up time.
It's an amazing project but it comes with a few challenges that we faced and solved!

Unfortunately GraalVM compilation should be [configured](https://www.graalvm.org/reference-manual/native-image/BuildConfiguration/) and this configuration can become costly and risky to maintain; luckly we completely own the scope of our CLI and we can automate the generation of such configuration by simply training it against a real cluster.
This boild down to run `sbt regenerateGraalVMConfig` every time we do a significant change to the CLI, this command will run an alternative `Main` of our application, intended to cover most of the possible codepaths.

Another disadvantage of GraalVM are the compilation times, currently the CLI takes about ~5 minutes to build the native package (with the command: `sbt graalvm-native-image:packageBin`), on the positive side, during development, we can still run our application on the JVM and, in particular, into the sbt interactive shell; this gives us the freedom to try out the Kubernetes commands against the cluster directly executing `sbt "run <command>"` and we can use the standard Java debugging tools during the entire development cycle.

The last issue we have had to overcome is the fact that GraalVM doesn't support cross-compilation, and, in this case, we simply leverage the CI(GH Actions) different runners to natively compile on the target architecture. We based our setup over this [precedent work](https://github.com/recursivecodes/simple-socket-fn-logger/blob/master/.github/workflows/simple-socket-fn-logger.yaml).

## Benefits

Being able to directly use Java dependencies keep us in the comfort zone of a well known echosystem.

Having an "internal" logging system that kick-in when necessary but gives the user the very same smooth experience as a "traditional" CLI is a big advantage.
In case something goes wrong we are able to expose the internal stack-traces by simply adding the option `-v trace` at the end of our command.
This makes debugging of user's issues pretty straightforward preserving the same ergonomics of the original CLI implemented in Go.

Try it with:
```
kubectl lp -v trace
```

A little nice feature is the fact that [Jackson](https://github.com/FasterXML/jackson) (used because already available from fabric8) provides out-of-the-box serializers for case classes to Json and Yaml. This way we can provide machine-readable output for any command with nearly 0 effort

Try it with:
```
kubectl lp -o json
kubectl lp -o yaml
```

Another very useful by-product is the fact that we have been able to re-use the Cli directly from ScalaTest (importing it as a dependency) and we have now full-Scala Integration tests without the need to run an external process and with helpful error messages in case something fails.
We plan as well to port the Cloudflow operator to use the very same CRD/CR definition, immediatly reducing to 0 the possibility to introduce discrepancy in between the the CLI and the operator itself.

## Conclusions

Rewriting a `kubectl` plugin from a standard Go stack to a more original approach has been challenging and rewarding.
We are really happy with the result, and we arguably improved under a number of aspects:

 - Improved user's experience in regard of ergonomy, feedback and validations
 - More features and functionalities have been added easily
 - The code-base is in a more familiar structure

Cloudflow migrated to the new CLI seamlessly with customers and users that made the switch mostly without noticing.

### References

 - kubectl plugins documentation : https://kubernetes.io/docs/tasks/extend-kubectl/kubectl-plugins/
 - fabric8 : https://github.com/fabric8io/kubernetes-client
 - graalVM : https://www.graalvm.org
