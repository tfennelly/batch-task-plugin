package hudson.plugins.batch_task;

import hudson.Launcher;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Project;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link Publisher} that triggers batch tasks of other projects.
 *
 * @author Kohsuke Kawaguchi
 */
public class BatchTaskInvoker extends Publisher {
    /**
     * What task to invoke?
     */
    public static final class Config {
        public final String project;
        public final String task;

        public Config(String project, String task) {
            this.project = project;
            this.task = task;
        }

        public Config(JSONObject source) {
            this(source.getString("project").trim(), source.getString("task").trim());
        }

        public boolean invoke(BuildListener listener) {
            PrintStream logger = listener.getLogger();

            AbstractProject<?,?> p = Hudson.getInstance().getItemByFullName(project, AbstractProject.class);
            if(p==null) {
                listener.error("No such project exists: "+project);
                return false;
            }

            BatchTaskProperty bp = p.getProperty(BatchTaskProperty.class);
            if(bp==null) {
                listener.error("No such task exists: "+task+". In fact, no batch tasks exist at all");
                return false;
            }

            BatchTask task = bp.getTask(this.task);
            if(task==null) {
                listener.error("No such task exists: "+task+". Perhaps you meant "+
                    bp.findNearestTask(this.task).name);
                return false;
            }

            logger.println("Invoking "+project+" - "+task);
            Hudson.getInstance().getQueue().add(task,0);
            return true;
        }
    }

    private final Config[] configs;

    public BatchTaskInvoker(Config[] configs) {
        this.configs = configs;
    }

    public BatchTaskInvoker(JSONObject source) {
        List<Config> configs = new ArrayList<Config>();
        for( Object o : JSONArray.fromObject(source.get("config")) )
            configs.add(new Config((JSONObject)o));
        this.configs = configs.toArray(new Config[configs.size()]);
    }

    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        for (Config config : configs)
            config.invoke(listener);
        return true;
    }

    public Descriptor<Publisher> getDescriptor() {
        return DescriptorImpl.INSTANCE;
    }

    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        private DescriptorImpl() {
            super(BatchTaskInvoker.class);
        }

        public String getDisplayName() {
            return "Invoke batch tasks of other projects";
        }

        public boolean isApplicable(AbstractProject<?,?> item) {
            return true;
//            // this is unlikely to be useful for standard module types,
//            // so disable from there for now.
//
//            // the real target of this feature is the promoted-builds plugin.
//
//            Object o = item; // avoid javac bug
//
//            if(o instanceof Project || o instanceof MavenModuleSet)
//                return false;
//            return true;
        }

        public static final DescriptorImpl INSTANCE = new DescriptorImpl();
    }
}