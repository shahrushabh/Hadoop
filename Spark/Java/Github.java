import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.*;
import org.apache.spark.api.java.function.PairFunction;

import scala.Tuple2;

public class Github {

	private final static int numOfReducers = 2;

	@SuppressWarnings("serial")
	public static void main(String[] args) throws Exception {

		if (args.length != 2) {
			System.err.println("Usage: Github <input> <output>");
			System.exit(1);
		}

		SparkConf sparkConf = new SparkConf().setAppName("Github");
		JavaSparkContext context = new JavaSparkContext(sparkConf);
		JavaRDD<String> github_data = context.textFile(args[0]);
		
		/**
		 * This generates repository data from github data that was read as csv file.
		 * Using the PairFunction, this creates a RDD that is mapped in following fashion. 
		 * Key = Language, Values = String(repository,stars)
		 */
		JavaPairRDD<String,String> repository_data = github_data.mapToPair(new PairFunction<String, String, String>() {
			@Override
			public Tuple2<String, String> call(String s) throws Exception {
				String[] tokens = s.split(",");
				String repository = tokens[0];
				String language = tokens[1];
				String stars = "0";
				// By default set to 0 so that it does not go out of bounds.
				if(tokens.length >= 12){
					stars = tokens[12];
				}
				
				String key = language;
				String value = repository+","+stars;
				return new Tuple2<String, String>(key, value);
			}
		});
		
		/**
		 * This groups the repository data by the language used in it.
		 */
		JavaPairRDD<String, Iterable<String>> grouped_repositories = repository_data.groupByKey();
		
		/**
		* Go through all the Pairs in grouped_repositories RDD and determine number of projects in the language,
		* and name of the repository with max starts.
		*/
		JavaPairRDD<Integer,String> sorted = grouped_repositories.mapToPair(new PairFunction<Tuple2<String, Iterable<String>>, Integer, String>() {
		    @Override
		    public Tuple2<Integer,String> call(Tuple2<String, Iterable<String>> item) throws Exception {
				// Extract languages most number of stars and total count of repositories using this language.
		        String language = item._1;
				String repoWithMaxStar = "";
				int maxStars = 0;
		        int count = 0;
		        for (String s : item._2) {
					String[] details = s.split(",");
					int numStars = Integer.parseInt(details[1]);
					if(numStars > maxStars){
						maxStars = numStars;
						repoWithMaxStar = details[0];
					}
		            count++;
		        }
				String record = language+"		"+Integer.toString(count)+"		"+repoWithMaxStar+"		"+Integer.toString(maxStars);
		        return new Tuple2<Integer,String>(new Integer(count),record);
		    }
		}).sortByKey(false);


		/**
		* Reformating output
		*/
		JavaRDD<String> results = sorted.map(new Function<Tuple2<Integer, String>, String>() {
		    @Override
		    public String call(Tuple2<Integer, String> item) throws Exception {
				return item._2;
		    }
		});
		
		results.saveAsTextFile(args[1]);
		context.stop();
		context.close();
	}
}