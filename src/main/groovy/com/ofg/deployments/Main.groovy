package com.ofg.deployments
import io.undertow.Undertow
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJacksonProvider
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer
import org.kohsuke.args4j.CmdLineException
import org.kohsuke.args4j.CmdLineParser
import org.kohsuke.args4j.Option

import javax.ws.rs.ApplicationPath
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Application

@Path("/")
class Main {

    private static UndertowJaxrsServer server

    private static final int DEFAULT_DEPLOY_PORT_NUMBER = 18081

    @Option(name = '-p', usage = """optional port number on which zookeeper rest server will be started.
It will expose one method on /stop to stop the server. Default is 18081""")
    private int controlPortNumber = DEFAULT_DEPLOY_PORT_NUMBER

    @Option(name = '-h', usage = "Host to bind to. Default is 0.0.0.0")
    private String controlHost = "0.0.0.0"

    @Option(name = '-r', usage = "Reposioty where the jars are uploaded. Default is 'http://nexus.microhackathon.pl/content/repositories/releases/'")
    private String repository = "http://nexus.microhackathon.pl/content/repositories/releases/"

    private static Map<String, ProcessWithDeployment> spawnedProcesses = new HashMap<>()

    @Option(name = '-dir', usage = 'Dir where jars will be downloaded. Default is jars')
    private File deploymentDir = new File("jars")

    @Option(name = '-logs', usage = 'Dir where logs will be redirected. Default is logs')
    private File logsDir = new File("logs")

    @Option(name = '-j', usage = "java binary. Default is 'java'")
    private String java = "java"

    @GET
    @Path("/stop")
    public String stop() {
        try {
            println "Stopping the deployment server"
            scheduleShutdownIn1Second()
            return "Deployment server stopped"
        } catch (Exception e) {
            return e.toString()
        }
    }

    @GET
    @Path("/list")
    @Produces("application/json")
    public List<Deployment> list() {
        return spawnedProcesses.values().collect {it.deployment}
    }

    @POST
    @Path("/deploy")
    @Consumes("application/json")
    public String deploy(Deployment deployment) {
        println "new deployment started $deployment"
        File jar = new File(deploymentDir, "${deployment.artifactId}-${deployment.version}.jar")

        if (!jar.exists()) {
            println "Got new version, downloading"
            jar.withOutputStream {
                it << new URL("${repository}${deployment.groupId.replaceAll('\\.', '/')}/${deployment.artifactId}/${deployment.version}/${deployment.artifactId}-${deployment.version}.jar").openStream()
            }

            println "finished downloading"
        }

        println "deploying"

        synchronized (spawnedProcesses) {
            // make sure one deployment is done at a time
            if (spawnedProcesses.get(deployment.uniqueId()) != null) {
                Process oldProcess = spawnedProcesses.get(deployment.uniqueId()).process
                println "killing old process"
                oldProcess.destroy()
                oldProcess.waitFor()

                println "Old process killed"
            }

            String command = "$java ${deployment.jvmParams} -jar ${jar.absolutePath}"
            println "command $command"
            Process proc = command.execute()

            spawnedProcesses.put(deployment.uniqueId(), new ProcessWithDeployment(process: proc, deployment: deployment))

            File output = new File(logsDir, "${deployment.uniqueId()}.log")

            Writer writer = new FileWriter(output)

            proc.consumeProcessOutput(writer, writer)

            new Thread({
                // flush the writier, so we get realtime logs
                while (proc.alive) {
                    writer.flush()
                    Thread.sleep(100)
                }
            }).start()
        }

        return "pozdrawiam"
    }

    private static void scheduleShutdownIn1Second() {
        new Thread({
            Thread.sleep(1000)
            shutdown()
        }).start()
    }

    public static void main(String[] args) {
        try {
            new Main().doMain(args)

        } catch (Exception e) {
            e.printStackTrace()
            shutdown()
        }
    }

    private static void shutdown() {
        spawnedProcesses.values().each {
            it.process.destroy()
            it.process.waitFor()
        }

        server.stop()
        println "rest stopped"
        System.exit(-1)
    }

    private void doMain(String[] args) {
        deploymentDir.mkdirs()
        logsDir.mkdirs()

        CmdLineParser parser = new CmdLineParser(this)

        try {
            parser.parseArgument(args);
            startDeploymentServer()
        } catch (CmdLineException e) {
            System.err.println(e.getMessage())
            parser.printUsage(System.err)
        }
    }

    private void startDeploymentServer() {
        server = new UndertowJaxrsServer().start(Undertow.builder()
                .addHttpListener(controlPortNumber, controlHost))
        server.deploy(RestApp)
        println "Deployment server started with on port [$controlPortNumber]"
    }

    @ApplicationPath("/")
    static class RestApp extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return [ResteasyJacksonProvider, Main] as Set
        }
    }

}
