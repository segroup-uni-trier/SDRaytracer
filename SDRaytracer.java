
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Container;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Dimension;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/* Implementation of a very simple Raytracer
   Stephan Diehl, Universität Trier, 2010-2016
*/



public class SDRaytracer extends JFrame
{
   private static final long serialVersionUID = 1L;
   boolean profiling=false;
   int width=1000;
   int height=1000;
   
   Future[] futureList= new Future[width];
   int nrOfProcessors = Runtime.getRuntime().availableProcessors();
   ExecutorService eservice = Executors.newFixedThreadPool(nrOfProcessors);
   
   int maxRec=3;
   int rayPerPixel=1;
   int startX, startY, startZ;

   List<Triangle> triangles;

   Light mainLight  = new Light(new Vec3D(0,100,0), new RGB(0.1f,0.1f,0.1f));

   Light lights[]= new Light[]{ mainLight
                                ,new Light(new Vec3D(100,200,300), new RGB(0.5f,0,0.0f))
                                ,new Light(new Vec3D(-100,200,300), new RGB(0.0f,0,0.5f))
                                //,new Light(new Vec3D(-100,0,0), new RGB(0.0f,0.8f,0.0f))
                              };

   RGB [][] image= new RGB[width][height];
   
   float fovx=(float) 0.628;
   float fovy=(float) 0.628;
   RGB ambient_color=new RGB(0.01f,0.01f,0.01f);
   RGB background_color=new RGB(0.05f,0.05f,0.05f);
   RGB black=new RGB(0.0f,0.0f,0.0f);
   int y_angle_factor=4, x_angle_factor=-4;
   
public static void  main(String argv[])
  { 
  long start = System.currentTimeMillis();
  SDRaytracer sdr=new SDRaytracer();
  long end = System.currentTimeMillis();
  long time = end - start;
  System.out.println("time: " + time + " ms");
  System.out.println("nrprocs="+sdr.nrOfProcessors);
  }

void profileRenderImage(){
  long end, start, time;

  renderImage(); // initialisiere Datenstrukturen, erster Lauf verfälscht sonst Messungen
  
  for(int procs=1; procs<6; procs++) {

   maxRec=procs-1;
   System.out.print(procs);
   for(int i=0; i<10; i++)
     { start = System.currentTimeMillis();

       renderImage();

       end = System.currentTimeMillis();
       time = end - start;
       System.out.print(";"+time);
     }
    System.out.println("");
   }
}

SDRaytracer()
 {
   createScene();

   if (!profiling) renderImage(); else profileRenderImage();
   
   setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
   Container contentPane = this.getContentPane();
   contentPane.setLayout(new BorderLayout());
   JPanel area = new JPanel() {
            public void paint(Graphics g) {
              System.out.println("fovx="+fovx+", fovy="+fovy+", xangle="+x_angle_factor+", yangle="+y_angle_factor);
              if (image==null) return;
              for(int i=0;i<width;i++)
               for(int j=0;j<height;j++)
                { g.setColor(image[i][j].color());
                  // zeichne einzelnen Pixel
                  g.drawLine(i,height-j,i,height-j);
                }
            }
           };
           
   addKeyListener(new KeyAdapter()
         { public void keyPressed(KeyEvent e)
            { boolean redraw=false;
              if (e.getKeyCode()==KeyEvent.VK_DOWN)
                {  x_angle_factor--;
                   //mainLight.position.y-=10;
                  //fovx=fovx+0.1f;
                  //fovy=fovx;
                  //maxRec--; if (maxRec<0) maxRec=0;
                  redraw=true;
                }
              if (e.getKeyCode()==KeyEvent.VK_UP)
                {  x_angle_factor++;
                   //mainLight.position.y+=10;
                  //fovx=fovx-0.1f;
                  //fovy=fovx;
                  //maxRec++;if (maxRec>10) return;
                  redraw=true;
                }
              if (e.getKeyCode()==KeyEvent.VK_LEFT)
                { y_angle_factor--;
                  //mainLight.position.x-=10;
                  //startX-=10;
                  //fovx=fovx+0.1f;
                  //fovy=fovx;
                  redraw=true;
                }
              if (e.getKeyCode()==KeyEvent.VK_RIGHT)
                { y_angle_factor++;
                  //mainLight.position.x+=10;
                  //startX+=10;
                  //fovx=fovx-0.1f;
                  //fovy=fovx;
                  redraw=true;
                }
              if (redraw)
               { createScene();
                 renderImage();
                 repaint();
               }
            }
         });
         
        area.setPreferredSize(new Dimension(width,height));
        contentPane.add(area);
        this.pack();
        this.setVisible(true);
}
 
Ray eye_ray=new Ray();
double tan_fovx;
double tan_fovy;
 
void renderImage(){
   tan_fovx = Math.tan(fovx);
   tan_fovy = Math.tan(fovy);
   for(int i=0;i<width;i++)
   { futureList[i]=  (Future) eservice.submit(new RaytraceTask(this,i)); 
   }
   
    for(int i=0;i<width;i++)
       { try {
          RGB [] col = (RGB[]) futureList[i].get();
          for(int j=0;j<height;j++)
            image[i][j]=col[j];
         }
   catch (InterruptedException e) {}
   catch (ExecutionException e) {}
    }
   }
 


RGB rayTrace(Ray ray, int rec) {
   if (rec>maxRec) return black;
   IPoint ip = hitObject(ray);  // (ray, p, n, triangle);
   if (ip.dist>IPoint.epsilon)
     return lighting(ray, ip, rec);
   else
     return black;
}


IPoint hitObject(Ray ray) {
   IPoint isect=new IPoint(null,null,-1);
   float idist=-1;
   for(Triangle t : triangles)
     { IPoint ip = ray.intersect(t);
        if (ip.dist!=-1)
        if ((idist==-1)||(ip.dist<idist))
         { // save that intersection
          idist=ip.dist;
          isect.ipoint=ip.ipoint;
          isect.dist=ip.dist;
          isect.triangle=t;
         }
     }
   return isect;  // return intersection point and normal
}


RGB addColors(RGB c1, RGB c2, float ratio)
 { return new RGB( (c1.red+c2.red*ratio),
           (c1.green+c2.green*ratio),
           (c1.blue+c2.blue*ratio));
  }
  
RGB lighting(Ray ray, IPoint ip, int rec) {
  Vec3D point=ip.ipoint;
  Triangle triangle=ip.triangle;
  RGB color = addColors(triangle.color,ambient_color,1);
  Ray shadow_ray=new Ray();
   for(Light light : lights)
       { shadow_ray.start=point;
         shadow_ray.dir=light.position.minus(point).mult(-1);
         shadow_ray.dir.normalize();
         IPoint ip2=hitObject(shadow_ray);
         if(ip2.dist<IPoint.epsilon)
         {
           float ratio=Math.max(0,shadow_ray.dir.dot(triangle.normal));
           color = addColors(color,light.color,ratio);
         }
       }
     Ray reflection=new Ray();
     //R = 2N(N*L)-L)    L ausgehender Vektor
     Vec3D L=ray.dir.mult(-1);
     reflection.start=point;
     reflection.dir=triangle.normal.mult(2*triangle.normal.dot(L)).minus(L);
     reflection.dir.normalize();
     RGB rcolor=rayTrace(reflection, rec+1);
     float ratio =  (float) Math.pow(Math.max(0,reflection.dir.dot(L)), triangle.shininess);
     color = addColors(color,rcolor,ratio);
     return(color);
  }

  void createScene()
   { triangles = new ArrayList<Triangle>();

   
     Cube.addCube(triangles, 0,35,0, 10,10,10,new RGB(0.3f,0,0),0.4f);       //rot, klein
     Cube.addCube(triangles, -70,-20,-20, 20,100,100,new RGB(0f,0,0.3f),.4f);
     Cube.addCube(triangles, -30,30,40, 20,20,20,new RGB(0,0.4f,0),0.2f);        // grün, klein
     Cube.addCube(triangles, 50,-20,-40, 10,80,100,new RGB(.5f,.5f,.5f), 0.2f);
     Cube.addCube(triangles, -70,-26,-40, 130,3,40,new RGB(.5f,.5f,.5f), 0.2f);


     Matrix mRx=Matrix.createXRotation((float) (x_angle_factor*Math.PI/16));
     Matrix mRy=Matrix.createYRotation((float) (y_angle_factor*Math.PI/16));
     Matrix mT=Matrix.createTranslation(0,0,200);
     Matrix m=mT.mult(mRx).mult(mRy);
     m.print();
     m.apply(triangles);
   }

}

class RaytraceTask implements Callable
{ SDRaytracer tracer;
  int i;
  RaytraceTask(SDRaytracer t, int ii) { tracer=t; i=ii; }

  public RGB[] call()
   { RGB[] col=new RGB[tracer.height];
     for (int j=0;j<tracer.height;j++)
       {  tracer.image[i][j]=new RGB(0,0,0);
            for(int k=0;k<tracer.rayPerPixel;k++)
            { double di=i+(Math.random()/2-0.25);
              double dj=j+(Math.random()/2-0.25);
              if (tracer.rayPerPixel==1) { di=i; dj=j; }
              Ray eye_ray=new Ray();
              eye_ray.setStart(tracer.startX, tracer.startY, tracer.startZ);   // ro
              eye_ray.setDir  ((float) (((0.5 + di) * tracer.tan_fovx * 2.0) / tracer.width - tracer.tan_fovx),
                            (float) (((0.5 + dj) * tracer.tan_fovy * 2.0) / tracer.height - tracer.tan_fovy),
                            (float) 1f);    // rd
             eye_ray.normalize();
             col[j]= tracer.addColors(tracer.image[i][j],tracer.rayTrace(eye_ray,0),1.0f/tracer.rayPerPixel);
            }
       }
     return col;
   }
}

class Vec3D {
  float x, y, z, w=1;
  Vec3D(float xx, float yy, float zz) { x=xx; y=yy; z=zz; }
  Vec3D(float xx, float yy, float zz, float ww) { x=xx; y=yy; z=zz; w=ww; }
  Vec3D add(Vec3D v)
    { return new Vec3D(x+v.x, y+v.y, z+v.z); }
  Vec3D minus(Vec3D v)
    { return new Vec3D(x-v.x, y-v.y, z-v.z); }
  Vec3D mult(float a)
    { return new Vec3D(a*x, a*y, a*z); }

  void normalize()
    {  float dist = (float) Math.sqrt( (x * x)+(y * y)+(z * z) );
       x = x / dist;
       y = y / dist;
       z = z / dist;
   }
   
  float dot(Vec3D v) { return x*v.x+y*v.y+z*v.z; }
  
  Vec3D cross(Vec3D v) {
    return new Vec3D( y*v.z-z*v.y, z*v.x-x*v.z, x*v.y-y*v.x);
  }
}

class Triangle
{  Vec3D p1,p2,p3;
   RGB color;
   Vec3D normal;
   float shininess;
   
   Triangle(Vec3D pp1, Vec3D pp2, Vec3D pp3, RGB col, float sh)
    { p1=pp1; p2=pp2; p3=pp3; color=col; shininess=sh;
      Vec3D e1=p2.minus(p1),
            e2=p3.minus(p1);
      normal=e1.cross(e2);
      normal.normalize();
    }
}

   
class Ray {
   Vec3D start=new Vec3D(0,0,0);
   Vec3D dir=new Vec3D(0,0,0);
   
   void setStart(float x, float y, float z) { start=new Vec3D(x,y,z); }
   void setDir(float dx, float dy, float dz) { dir=new Vec3D(dx, dy, dz); }
   void normalize() {  dir.normalize(); }
   
   // see Möller&Haines, page 305
   IPoint intersect(Triangle t)
    { float epsilon=IPoint.epsilon;
      Vec3D e1 = t.p2.minus(t.p1);
      Vec3D e2 = t.p3.minus(t.p1);
      Vec3D p =  dir.cross(e2);
      float a = e1.dot(p);
      if ((a>-epsilon) && (a<epsilon)) return new IPoint(null,null,-1);
      float f = 1/a;
      Vec3D s = start.minus(t.p1);
      float u = f*s.dot(p);
      if ((u<0.0) || (u>1.0)) return new IPoint(null,null,-1);
      Vec3D q = s.cross(e1);
      float v = f*dir.dot(q);
      if ((v<0.0) || (u+v>1.0)) return new IPoint(null,null,-1);
      // intersection point is u,v
      float dist=f*e2.dot(q);
      if (dist<epsilon) return new IPoint(null,null,-1);
      Vec3D ip=t.p1.mult(1-u-v).add(t.p2.mult(u)).add(t.p3.mult(v));
      //DEBUG.debug("Intersection point: "+ip.x+","+ip.y+","+ip.z);
      return new IPoint(t,ip,dist);
    }
}

class IPoint {
  final static float epsilon=0.0001f;
  Triangle triangle;
  Vec3D ipoint;
  float dist;
  IPoint(Triangle tt, Vec3D ip, float d) { triangle=tt; ipoint=ip; dist=d; }
}

class Light {
 RGB color;
 Vec3D position;
 Light(Vec3D pos, RGB c) { position=pos; color=c; }
}

class DEBUG {
 static void debug(String s) { } // System.err.println(s); }
}

class Cube
 {
   public static void addCube(List<Triangle> triangles, int x, int y, int z, int w, int h, int d, RGB c, float sh)
    {  //front
       triangles.add(new Triangle(new Vec3D(x,y,z), new Vec3D(x+w,y,z), new Vec3D(x,y+h,z), c, sh));
       triangles.add(new Triangle(new Vec3D(x+w,y,z), new Vec3D(x+w,y+h,z), new Vec3D(x,y+h,z), c, sh));
       //left
       triangles.add(new Triangle(new Vec3D(x,y,z+d), new Vec3D(x,y,z), new Vec3D(x,y+h,z), c, sh));
       triangles.add(new Triangle(new Vec3D(x,y+h,z), new Vec3D(x,y+h,z+d), new Vec3D(x,y,z+d), c, sh));
       //right
       triangles.add(new Triangle(new Vec3D(x+w,y,z), new Vec3D(x+w,y,z+d), new Vec3D(x+w,y+h,z), c, sh));
       triangles.add(new Triangle(new Vec3D(x+w,y+h,z), new Vec3D(x+w,y,z+d), new Vec3D(x+w,y+h,z+d), c, sh));
       //top
       triangles.add(new Triangle(new Vec3D(x+w,y+h,z), new Vec3D(x+w,y+h,z+d), new Vec3D(x,y+h,z), c, sh));
       triangles.add(new Triangle(new Vec3D(x,y+h,z), new Vec3D(x+w,y+h,z+d), new Vec3D(x,y+h,z+d), c, sh));
       //bottom
       triangles.add(new Triangle(new Vec3D(x+w,y,z), new Vec3D(x,y,z), new Vec3D(x,y,z+d), c, sh));
       triangles.add(new Triangle(new Vec3D(x,y,z+d), new Vec3D(x+w,y,z+d), new Vec3D(x+w,y,z), c, sh));
       //back
       triangles.add(new Triangle(new Vec3D(x,y,z+d),  new Vec3D(x,y+h,z+d), new Vec3D(x+w,y,z+d), c, sh));
       triangles.add(new Triangle(new Vec3D(x+w,y,z+d), new Vec3D(x,y+h,z+d), new Vec3D(x+w,y+h,z+d), c, sh));

    }
 }
 
 class RGB {
   float red,green,blue;
   Color color;
   
   RGB(float r, float g, float b)
    { if (r>1) r=1; else if (r<0) r=0;
      if (g>1) g=1; else if (g<0) g=0;
      if (b>1) b=1; else if (b<0) b=0;
      red=r; green=g; blue=b;
    }
    
   Color color()
    { if (color!=null) return color;
      color=new Color((int) (red*255),(int) (green*255), (int) (blue*255));
      return color;
    }

}


class Matrix {
   float val [][] = new float[4][4];

   Matrix() { }
   Matrix(float [][] vs) { val=vs; }
   
   void print()
    { for(int i=0;i<4;i++)
       { for(int j=0;j<4;j++)
          { System.out.print(" "+(val[i][j]+"       ").substring(0,8)); }
         System.out.println();
       }
    }


   Matrix mult(Matrix m)
    { Matrix r=new Matrix();
      for(int i=0;i<4;i++)
       for(int j=0;j<4;j++)
        { float sum=0f;
          for(int k=0;k<4;k++) sum=sum+val[i][k]*m.val[k][j];
          r.val[i][j]=sum;
        }
      return r;
     }
     
    Vec3D mult(Vec3D v)
    { Vec3D temp = new Vec3D( val[0][0]*v.x+val[0][1]*v.y+val[0][2]*v.z+val[0][3]*v.w,
                              val[1][0]*v.x+val[1][1]*v.y+val[1][2]*v.z+val[1][3]*v.w,
                              val[2][0]*v.x+val[2][1]*v.y+val[2][2]*v.z+val[2][3]*v.w,
                              val[3][0]*v.x+val[3][1]*v.y+val[3][2]*v.z+val[3][3]*v.w );
      //return new Vec3D(temp.x/temp.w,temp.y/temp.w,temp.z/temp.w,1);
      temp.x=temp.x/temp.w; temp.y=temp.y/temp.w; temp.z=temp.z/temp.w; temp.w=1;
      return temp;
    }

   static Matrix createId()
    { return new Matrix(new float[][]{
         { 1, 0, 0, 0},
         { 0, 1, 0, 0 },
         { 0, 0, 1, 0 },
         { 0, 0, 0, 1 } });
    }
    
   static Matrix createXRotation(float angle)
    { return new Matrix(new float[][]{
         { 1, 0, 0 , 0},
         { 0, (float)Math.cos(angle), (float)-Math.sin(angle), 0 },
         { 0, (float)Math.sin(angle), (float)Math.cos(angle),0 },
         { 0 , 0, 0, 1 } });
    }

   static Matrix createYRotation(float angle)
    { return new Matrix(new float[][]{
         { (float)Math.cos(angle), 0, (float)Math.sin(angle), 0 },
         { 0, 1, 0, 0 },
         { (float)-Math.sin(angle), 0, (float)Math.cos(angle), 0 },
         { 0, 0, 0, 1 } });
    }

   static Matrix createZRotation(float angle)
    { return new Matrix(new float[][]{
         { (float)Math.cos(angle), (float)-Math.sin(angle), 0, 0 },
         { (float)Math.sin(angle), (float)Math.cos(angle), 0, 0 },
         { 0, 0, 1, 0 },
         { 0, 0, 0, 1 }  });
    }

    static Matrix createTranslation(float dx, float dy, float dz)
    { return new Matrix(new float[][]{
         { 1, 0, 0, dx },
         { 0, 1, 0, dy },
         { 0, 0, 1, dz },
         { 0, 0, 0, 1  } });
    }

   void apply(List<Triangle> ts)
    { for(Triangle t: ts)
       { t.p1=this.mult(t.p1);
         t.p2=this.mult(t.p2);
         t.p3=this.mult(t.p3);
         Vec3D e1=t.p2.minus(t.p1),
               e2=t.p3.minus(t.p1);
         t.normal=e1.cross(e2);
         t.normal.normalize();
       }
    }
}


