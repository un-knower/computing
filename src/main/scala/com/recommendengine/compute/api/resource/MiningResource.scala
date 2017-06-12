package com.recommendengine.compute.api.resource

import java.net.URLDecoder

import scala.collection.mutable.Map

import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.hbase.CellUtil
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.HConnectionManager
import org.apache.hadoop.hbase.util.Bytes

import com.google.gson.Gson
import com.recommendengine.compute.api.RecServer
import com.recommendengine.compute.api.model.WebPage
import com.recommendengine.compute.conf.ComputingConfiguration
import com.recommendengine.compute.metadata.Computing
import com.recommendengine.compute.utils.TextSplit

import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import java.util.StringTokenizer
import scala.collection.mutable.ArrayBuilder
import javax.ws.rs.core.MediaType
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.FileNotFoundException
import org.apache.spark.sql.SparkSession
import org.apache.spark.ml.feature.HashingTF
import org.apache.spark.ml.classification.NaiveBayesModel
import org.apache.spark.ml.feature.IDFModel
import org.apache.spark.ml.linalg.DenseVector
import org.apache.spark.ml.feature.StringIndexer
import org.apache.spark.ml.feature.StringIndexerModel

object MiningResource {

  val conf = ComputingConfiguration.createWithHbase

  val models = Map[String, Any]()

  var idfModel = Map[String, Any]()

  var label = Map[String, Array[String]]()

  def toTF(str: String): Unit = {

  }

  //  def toTFIDF(idf: Vector, v: Vector): Vector = {
  //
  //    val n = v.size
  //
  //    v match {
  //      case SparseVector(size, indices, values) => {
  //        val nnz = indices.size
  //        val newValues = new Array[Double](nnz)
  //        var k = 0
  //        while (k < nnz) {
  //          newValues(k) = values(k) * idf(indices(k))
  //          k += 1
  //        }
  //        Vectors.sparse(n, indices, newValues)
  //      }
  //
  //      case DenseVector(values) => {
  //        var j = 0
  //        val newValues = new Array[Double](n)
  //        while (j < n) {
  //          newValues(j) = v(j) * idf(j)
  //          j += 1
  //        }
  //        Vectors.dense(newValues)
  //      }
  //
  //      case other => throw new UnsupportedOperationException(
  //        s"Only sparse and dense vectors are supported but got ${other.getClass}.")
  //    }
  //
  //  }

}

@Path(value = "/mining")
class MiningResource extends AbstractResource {

  @GET
  @Path(value = "/test")
  def test(): String = {

    return "here are you"
  }

  @POST
  @Path("/data-list")
  def dataList(taskConf: String): String = {

    val encode = URLDecoder.decode(taskConf.substring(9), "UTF-8")

    null
  }

  @POST
  @Path("/classify")
  @Produces(Array(MediaType.TEXT_HTML))
  def classify2(text: String, @QueryParam("biz_code") bizCode: String, @QueryParam("ss_code") ssCode: String, @QueryParam("model") modelClass: String): Any = {

    println("comeing here!!!!!!")
    println(text)
    require(bizCode != null && ssCode != null, s"参数不足或者为空!!=>[biz_code=$bizCode],[ss_code=$ssCode],[model=$modelClass]")
    require(text != null, s"内容不能为空!!=>$text")

    val ss = SparkSession.builder().master("local[*]").getOrCreate()
    val bsCode = bizCode + "_" + ssCode

    println(bsCode)
    val conf = MiningResource.conf

    if (MiningResource.models.get(bsCode) == None) {
      val model = NaiveBayesModel.load(conf.get("default.model.path") + "/" + bsCode + "/" + NaiveBayesModel.getClass.getSimpleName)
      MiningResource.models.put(bsCode, model)
    }
    if (MiningResource.idfModel.get(bsCode) == None) {
  
      MiningResource.idfModel.put(bsCode, IDFModel.load(conf.get("default.model.path") + "/" + bsCode + "/" + IDFModel.getClass.getSimpleName))
    }
  
    if (MiningResource.label.get(bsCode) == None) {
      val stringIndex = StringIndexerModel.load(conf.get("default.model.path") + "/" + bsCode + "/" + StringIndexerModel.getClass.getSimpleName)
      MiningResource.label.put(bsCode, stringIndex.labels)
    }
  
    val labels = MiningResource.label.get(bsCode).get
  
    val idfModel = MiningResource.idfModel.get(bsCode).get
    val predictModel = MiningResource.models.get(bsCode).get
//    if (predictModel == None || idfModel == None || labels==None)
//      throw new NullPointerException("尚未训练模型")
  
  
    
    val data = ss.createDataFrame(Seq((0, TextSplit.process(text).split(",")))).toDF("id", "words")
  
    val tf = new HashingTF().setInputCol("words").setOutputCol("rowFeatures").transform(data)
  
    val tfidf = idfModel.asInstanceOf[IDFModel].transform(tf)
    val predicts = predictModel.asInstanceOf[NaiveBayesModel].transform(tfidf).select("probability").collect()(0).getAs[DenseVector](0).toArray
    val labelWithPredicts = for (i <- 0 until labels.length) yield (labels(i), predicts(i))

    val result=labelWithPredicts.filter(_._2>0).map(f=>(f._1,1.0/(Math.abs(Math.log10(f._2))+1.0)))
    val count=result.map(_._2).reduce(_ + _)
    result.map(f=>(f._1,f._2/count)).sortWith((x, y) => x._2 > y._2).toArray.mkString(",")
  }
  
  def getOrElseMeaningFulUser(text: String, @QueryParam("biz_code") bizCode: String, @QueryParam("ss_code") ssCode: String, @QueryParam("model") modelClass: String): Any = {
  
  }

}