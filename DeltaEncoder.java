import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;

public class DeltaEncoder 
{
    public static void main(String[] args)
    {
        int     count;
        Display display;

        count = args.length;
        if(count != 1)
            System.out.println("Usage: java DeltaEncoder <filename>");
        else
            display = new Display(args[0]);
    }
}

class Display extends Frame implements ImageObserver
{
    FileOutputStream out;
    Image            image; 
    Dimension        dimension;
    ImageCanvas      image_canvas;
    PixelGrabber     pixel_grabber;
    ColorModel       color_model;
    int              minimum_delta; 
    int              number_of_bytes, size;
    int              number_of_bits;
    int              previous_size, current_size;
    int              maximum_delta, pixel_shift; 
    int              delta[], shrink[], workspace[];
    byte             even_processed_bit_strings[];
    byte             odd_processed_bit_strings[];
    byte             temp[];
    int              start_fraction[], end_fraction[], number_of_pixels[];
    int              red[], green[], difference[], blue[], pixel[], original_pixel[];
    int              xdim, ydim, i, j, k;
    int              intermediate_xdim, intermediate_ydim;
    int              even_xdim, even_ydim;
    int              number_of_different_values;
    boolean          errored = false;
    int              cursor_length;

    int              pixel_depth_quantize_amount = 0;
    TextArea         current_pixel_depth;
    int              error_amount = 0; 
    TextArea         current_error;
    int              current_delta_quantization_amount = 0;
    TextArea         current_delta_quantization;

    Scrollbar        error_scrollbar;
    int              error_scrollbar_range, error_position;
    ErrorScrollbarHandler error_handler; 
    Panel            error_panel; 
    int              previous_error = -1;
    int              previous_delta = 10;

    double           zero_one_ratio;
    double           new_zero_one_ratio[];

    Panel            scrollbar_panel; 
    Panel            button_panel; 
    Panel            parameter_panel;

    int              CurrentResolution = 1;
    int              PreviousResolution = -1;
    double           scale_factor;
    int              number_of_iterations;

    DisplayWindowAdapter windowHandler;

    Button           go_button;
    GoButtonHandler  go_handler;
    Button           reload_button;
    ReloadButtonHandler  reload_handler;
    Button           resolution_button;
    TextArea         user_information;
    double           delta_amount_of_compression;
    double           original_amount_of_compression;
    File             image_file;

    public Display(String filename)
    {
        try
        {
            image = Toolkit.getDefaultToolkit().getImage(filename);
        }
        catch(Exception e)
        {
            System.out.println(e.getMessage());
            System.exit(1);
        }
        color_model = ColorModel.getRGBdefault();
        xdim = -1;
        while(xdim < 0)
        {
            xdim = image.getWidth(this);
        }
        ydim = -1;
        while(ydim < 0)
        { 
            ydim = image.getHeight(this); 
        }
        if(xdim % 2 != 0)
            even_xdim = xdim - 1;
        else
            even_xdim = xdim;

        if(ydim % 2 != 0)
            even_ydim = ydim - 1;
        else
            even_ydim = ydim;

        red          = new int[xdim * ydim];
        green        = new int[xdim * ydim];
        blue         = new int[xdim * ydim];
        difference   = new int[xdim * ydim];
        workspace    = new int[xdim * ydim];
        shrink       = new int[xdim * ydim];
        delta        = new int[xdim * ydim];
        original_pixel = new int[xdim * ydim];
        pixel          = new int[xdim * ydim];
        even_processed_bit_strings  = new byte[4 * xdim * ydim];
        odd_processed_bit_strings   = new byte[5 * xdim * ydim];
        temp                        = new byte[4 * xdim * ydim];
        if(xdim > ydim)
        {
            start_fraction   = new int[xdim];
            end_fraction     = new int[xdim];
            number_of_pixels = new int[xdim];
        }
        else
        {
            start_fraction   = new int[ydim];
            end_fraction     = new int[ydim];
            number_of_pixels = new int[ydim];
        }
        new_zero_one_ratio = new double[1];

        pixel_grabber = new PixelGrabber(image, 0, 0, xdim, ydim, original_pixel, 0, xdim);
        try
        {
            pixel_grabber.grabPixels();
        }
        catch(InterruptedException e)
        {
            System.err.println(e.toString());
        }
        if((pixel_grabber.getStatus() & ImageObserver.ABORT) != 0)
        {
            System.err.println("Error grabbing pixels.");
            System.exit(1);
        }
        else
        {
            if(even_xdim != xdim || even_ydim != ydim)
                extract(original_pixel, xdim, ydim, 0, 0, original_pixel, even_xdim, even_ydim);
            image = createImage(new MemoryImageSource(even_xdim, even_ydim, color_model, original_pixel, 0, even_xdim));
        }
        image_canvas = new ImageCanvas(image);
        this.add("Center", image_canvas);
        windowHandler = new DisplayWindowAdapter();
        this.addWindowListener(windowHandler);

        user_information = new TextArea("", 2, xdim, TextArea.SCROLLBARS_NONE);
        user_information.setEditable(false);

        go_button  = new Button();
        go_button.setLabel("Delta encode.");
        go_handler = new GoButtonHandler(this);
        go_button.addActionListener(go_handler);

        reload_button  = new Button();
        reload_button.setLabel("Reload.");
        reload_handler = new ReloadButtonHandler(this);
        reload_button.addActionListener(reload_handler);

        button_panel = new Panel();
        button_panel.add(go_button);
        button_panel.add(reload_button);
        
        error_scrollbar_range = 7;
        error_position        = 0;
        cursor_length = error_scrollbar_range / 5;
        if(cursor_length == 0) 
            cursor_length = 1;
        error_scrollbar = new Scrollbar(Scrollbar.HORIZONTAL, error_position, cursor_length, 0, error_scrollbar_range + cursor_length);
        current_error = new TextArea("", 1, 4, TextArea.SCROLLBARS_NONE);
        current_error.setText(" " + Integer.toString(error_position));
        current_error.setEditable(false);
        error_panel     = new Panel(new BorderLayout());
        error_panel.add("North", new Label("Spatial Quantization"));
        error_panel.add("Center", error_scrollbar);
        error_panel.add("West", current_error);
        error_amount = 0;
        error_handler = new ErrorScrollbarHandler();
        error_scrollbar.addAdjustmentListener(error_handler);

        int pixel_depth_scrollbar_range = 7;
        int pixel_depth_position        = 0;
        cursor_length = pixel_depth_scrollbar_range / 5;
        if(cursor_length == 0) 
            cursor_length = 1;
        Scrollbar pixel_depth_scrollbar = new Scrollbar(Scrollbar.HORIZONTAL, pixel_depth_position, cursor_length, 0, pixel_depth_scrollbar_range + cursor_length);
        current_pixel_depth = new TextArea("", 1, 4, TextArea.SCROLLBARS_NONE);
        current_pixel_depth.setText(" " + Integer.toString(pixel_depth_position));
        current_pixel_depth.setEditable(false);
        Panel pixel_depth_panel     = new Panel(new BorderLayout());
        pixel_depth_panel.add("North", new Label("Pixel Depth Quantization"));
        pixel_depth_panel.add("Center", pixel_depth_scrollbar);
        pixel_depth_panel.add("West", current_pixel_depth);
        PixelDepthScrollbarHandler pixel_depth_handler = new PixelDepthScrollbarHandler();
        pixel_depth_scrollbar.addAdjustmentListener(pixel_depth_handler);

        int delta_scrollbar_range = 7;
        int delta_position  = 0;
        cursor_length = delta_scrollbar_range / 5;
        if(cursor_length == 0) 
            cursor_length = 1;
        Scrollbar delta_scrollbar = new Scrollbar(Scrollbar.HORIZONTAL, delta_position, cursor_length, 0, delta_scrollbar_range + cursor_length);
        current_delta_quantization = new TextArea("", 1, 4, TextArea.SCROLLBARS_NONE);
        current_delta_quantization.setText(" " + Integer.toString(delta_position));
        current_delta_quantization.setEditable(false);
        Panel delta_panel     = new Panel(new BorderLayout());
        delta_panel.add("North", new Label("Error Quantization"));
        delta_panel.add("Center", delta_scrollbar);
        delta_panel.add("West", current_delta_quantization);
        DeltaScrollbarHandler delta_handler = new DeltaScrollbarHandler();
        delta_scrollbar.addAdjustmentListener(delta_handler);

        Panel top_panel = new Panel(new BorderLayout());
        top_panel.add("North", pixel_depth_panel);
        top_panel.add("South", delta_panel);
        Panel scrollbar_panel = new Panel(new BorderLayout());
        scrollbar_panel.add("North", top_panel);
        scrollbar_panel.add("South", error_panel);

        parameter_panel     = new Panel(new BorderLayout());
        parameter_panel.add("North", button_panel);
        parameter_panel.add("Center", scrollbar_panel);
        parameter_panel.add("South", user_information);

        this.add("South", parameter_panel);

        image_file = new File(filename);
        original_amount_of_compression = (double)image_file.length();
        original_amount_of_compression = (1. - (original_amount_of_compression / (xdim * ydim * 3)));
        original_amount_of_compression = (double)((int)(1000. * original_amount_of_compression));
        original_amount_of_compression /= 1000.;
        user_information.setText("Original image...compression is " + original_amount_of_compression);
        this.setSize(even_xdim, even_ydim + 300);
        this.setVisible(true);
    }

    public boolean imageUpdate(Image image, int infoflags, int x, int y, int w, int h)
    {
        if((infoflags & (ERROR)) != 0)
            errored = true;
        boolean done = ((infoflags & (ERROR | FRAMEBITS | ALLBITS)) != 0);
        repaint(done ? 0 : 100);
        return !done;
    }
    
    class ImageCanvas extends Canvas
    {
        Image this_image;

        public ImageCanvas(Image image)
        {
            this_image = image;
        }
    
        public synchronized void PutImage(Image image, Graphics g)
        {
            this_image = image;
        }
    
        public synchronized void paint(Graphics g)
        {
            g.drawImage(this_image, 0, 0, this);
        }
    }

    //The following listener is used to terminate the 
    // program when the user closes the Frame object.
    class DisplayWindowAdapter extends WindowAdapter
    {
        public void windowClosing(WindowEvent e)
        {
            System.exit(0);
        }
    }   
    
    class GoButtonHandler implements ActionListener
    {
        Display this_display;
        int difference_number_of_bits; 
        int difference_shift;
        int difference_maximum_delta;
        int original_size; 
        int and_number_of_bits;
    
        GoButtonHandler(Display display)
        {
            this_display = display;
        }
    
        public void actionPerformed(ActionEvent e)
        {
            if(PreviousResolution != CurrentResolution)
            {
                PreviousResolution = CurrentResolution;
                for(i = 0; i < even_xdim * even_ydim; i++)
                    green[i] = (original_pixel[i] >> 8) & 0xff;        
                pixel_shift   = pixel_depth_quantize_amount;
                maximum_delta = 256;
                maximum_delta >>= pixel_shift;
                number_of_different_values = 2 * maximum_delta + 1;
                delta_amount_of_compression = 0;

                if(error_amount == 0)
                {
                    getDeltasFromPixels(green, delta, even_xdim, even_ydim, pixel_shift, maximum_delta);
                    GetPixelsFromDeltas(delta, difference, even_xdim, even_ydim, pixel_shift, maximum_delta);
                    for(i = 0; i < even_xdim * even_ydim; i++)
                        difference[i] -= green[i];
                    getDeltasFromPixels(green, delta, even_xdim, even_ydim, pixel_shift, maximum_delta);
                    number_of_bits = packStrings(delta, even_xdim * even_ydim, number_of_different_values, odd_processed_bit_strings);
                    original_size = number_of_bits;
                    size = even_xdim * even_ydim;
                    zero_one_ratio = (double) size / (double)number_of_bits;
                    size = (int)(zero_one_ratio * 100.);
                    zero_one_ratio = (double)size / 100.;
                    System.out.println("The green delta string is " + zero_one_ratio + " zeros.");
                    number_of_bits = compressBits(odd_processed_bit_strings, original_size, zero_one_ratio, even_processed_bit_strings);
                    if(number_of_bits != -1)
                    {
                        delta_amount_of_compression = number_of_bits / 8;
                        number_of_bits = decompressBits(even_processed_bit_strings, number_of_bits, odd_processed_bit_strings);
                    }
                    else
                    {
                        number_of_bits = original_size;
                        System.out.println("Data did not compress with or.");
                        and_number_of_bits = compressZeroAndOneBits(odd_processed_bit_strings, number_of_bits, even_processed_bit_strings);
                        if(and_number_of_bits > 0)
                        {
                            System.out.println("Data compressed with and.");
                            delta_amount_of_compression = and_number_of_bits / 8;
                        }
                        else
                        {
                            System.out.println("Data did not compress with and.");
                            delta_amount_of_compression = number_of_bits / 8;
                        }
                    }
                    System.out.println("Delta string is " + delta_amount_of_compression + " bytes long.");
                    number_of_bytes = unpackStrings(odd_processed_bit_strings, number_of_different_values, delta, even_xdim * even_ydim);
                    GetPixelsFromDeltas(delta, green, even_xdim, even_ydim, pixel_shift, maximum_delta);
                    shrinkAvg(difference, even_xdim, even_ydim, shrink);
                    int min = 256;
                    int max = -256;
                    for(i = 0; i < (even_xdim / 2) * (even_ydim / 2); i++)
                    {
                        if(shrink[i] > max)
                            max = shrink[i];
                        else if(shrink[i] < min)
                        min = shrink[i];
                    }
                    difference_maximum_delta = max - min;
                    difference_shift         = current_delta_quantization_amount;
                    if(difference_maximum_delta > 0 && pixel_shift > 2)
                    {
                        getDeltasFromPixels(shrink, delta, even_xdim / 2, even_ydim / 2, difference_shift, difference_maximum_delta);
                        number_of_different_values = 2 * difference_maximum_delta + 1;
                        difference_number_of_bits = packStrings(delta, (even_xdim / 2) * (even_ydim / 2), number_of_different_values, odd_processed_bit_strings);
                        size = (even_xdim / 2) * (even_ydim / 2);
                        zero_one_ratio = (double) size / (double)difference_number_of_bits;
                        size = (int)(zero_one_ratio * 100.);
                        zero_one_ratio = (double)size / 100.;
                        System.out.println("The difference delta string is " + zero_one_ratio + " zeros.");
                        original_size = difference_number_of_bits;
                        difference_number_of_bits = compressBits(odd_processed_bit_strings, difference_number_of_bits, zero_one_ratio, even_processed_bit_strings);
                        if(difference_number_of_bits != -1)
                        {
                            delta_amount_of_compression += difference_number_of_bits / 8;
                        }
                        else
                        {
                            System.out.println("Data did not compress with or.");
                            difference_number_of_bits = original_size;
                            and_number_of_bits = compressZeroAndOneBits(odd_processed_bit_strings, difference_number_of_bits, even_processed_bit_strings);
                            if(and_number_of_bits > 0)
                            {
                                System.out.println("Data compressed with and.");
                                delta_amount_of_compression += and_number_of_bits / 8;
                            }
                            else
                            {
                                System.out.println("Data did not compress with and.");
                                delta_amount_of_compression += difference_number_of_bits / 8;
                            }
                        }
                        System.out.println("Delta string is " + delta_amount_of_compression + " bytes long.");
                        GetPixelsFromDeltas(delta, shrink, even_xdim / 2, even_ydim / 2, difference_shift, difference_maximum_delta);
                        expandInterp(shrink, even_xdim / 2, even_ydim / 2, difference);
                        for(i = 0; i < even_xdim * even_ydim; i++)
                            green[i] -= difference[i];
                    } 
                } 
                else
                {
                    scale_factor      = 1. - .1 * (double)error_amount; 
                    intermediate_xdim = (int)((double)even_xdim * scale_factor);
                    intermediate_ydim = (int)((double)even_ydim * scale_factor);
                    System.out.println("The original xdim is " + even_xdim);
                    System.out.println("The original ydim is " + even_ydim);
                    System.out.println("The intermediate xdim is " + intermediate_xdim);
                    System.out.println("The intermediate ydim is " + intermediate_ydim);
                    avgAreaTransform(green, even_xdim, even_ydim, shrink, intermediate_xdim, intermediate_ydim, workspace, start_fraction, end_fraction, number_of_pixels);
                    getDeltasFromPixels(shrink, delta, intermediate_xdim, intermediate_ydim, pixel_shift, maximum_delta);
                    GetPixelsFromDeltas(delta, workspace, intermediate_xdim, intermediate_ydim, pixel_shift, maximum_delta);
                    avgAreaTransform(workspace, intermediate_xdim, intermediate_ydim, difference, even_xdim, even_ydim, red, start_fraction, end_fraction, number_of_pixels);
                    for(i = 0; i < even_xdim * even_ydim; i++)
                        difference[i] -= green[i];
                    number_of_different_values = 2 * maximum_delta + 1;
                    number_of_bits = packStrings(delta, intermediate_xdim * intermediate_ydim, number_of_different_values, odd_processed_bit_strings);
                    size = intermediate_xdim * intermediate_ydim;
                    zero_one_ratio = (double) size / (double)number_of_bits;
                    size = (int)(zero_one_ratio * 100.);
                    zero_one_ratio = (double)size / 100.;
                    System.out.println("The green delta string is " + zero_one_ratio + " zeros.");
                    original_size = number_of_bits;
                    number_of_bits = compressBits(odd_processed_bit_strings, original_size, zero_one_ratio, even_processed_bit_strings);
                    if(number_of_bits != -1)
                    {
                        delta_amount_of_compression = number_of_bits / 8;
                        number_of_bits = decompressBits(even_processed_bit_strings, number_of_bits, odd_processed_bit_strings);
                    }
                    else
                    {
                        number_of_bits = original_size;
                        System.out.println("Data did not compress with or.");
                        and_number_of_bits = compressZeroAndOneBits(odd_processed_bit_strings, number_of_bits, even_processed_bit_strings);
                        if(and_number_of_bits > 0)
                        {
                            System.out.println("Data compressed with and.");
                            delta_amount_of_compression = and_number_of_bits / 8;
                        }
                        else
                        {
                            System.out.println("Data did not compress with and.");
                            delta_amount_of_compression = number_of_bits / 8;
                            
                        }
                    }
                    System.out.println("Delta string is " + delta_amount_of_compression + " bytes long.");
                    number_of_different_values = 2 * maximum_delta + 1;
                    number_of_bytes = unpackStrings(odd_processed_bit_strings, number_of_different_values, delta, intermediate_xdim * intermediate_ydim);
                    GetPixelsFromDeltas(delta, shrink, intermediate_xdim, intermediate_ydim, pixel_shift, maximum_delta);
                    avgAreaTransform(shrink, intermediate_xdim, intermediate_ydim, green, even_xdim, even_ydim, workspace, start_fraction, end_fraction, number_of_pixels);
                    shrinkAvg(difference, even_xdim, even_ydim, shrink);
                    int min = 256;
                    int max = -256;
                    for(i = 0; i < (even_xdim / 2) * (even_ydim / 2); i++)
                    {
                        if(shrink[i] > max)
                            max = shrink[i];
                        else if(shrink[i] < min)
                            min = shrink[i];
                    }
                    difference_maximum_delta = max - min;
                    difference_shift         = current_delta_quantization_amount;
                    if(difference_maximum_delta > 0 && pixel_shift > 2)
                    {
                        getDeltasFromPixels(shrink, delta, even_xdim / 2, even_ydim / 2, difference_shift, difference_maximum_delta);
                        number_of_different_values = 2 * difference_maximum_delta + 1;
                        difference_number_of_bits = packStrings(delta, (even_xdim / 2) * (even_ydim / 2), number_of_different_values, odd_processed_bit_strings);
                        size = (even_xdim / 2) * (even_ydim / 2);
                        zero_one_ratio = (double) size / (double)difference_number_of_bits;
                        size = (int)(zero_one_ratio * 100.);
                        zero_one_ratio = (double)size / 100.;
                        System.out.println("The difference delta string is " + zero_one_ratio + " zeros.");
        
                        original_size = difference_number_of_bits;
                        difference_number_of_bits = compressBits(odd_processed_bit_strings, difference_number_of_bits, zero_one_ratio, even_processed_bit_strings);
                        if(difference_number_of_bits != -1)
                        {
                            delta_amount_of_compression += difference_number_of_bits / 8;
                        }
                        else
                        {
                            System.out.println("Difference data did not compress with or.");
                            difference_number_of_bits = original_size;
                            and_number_of_bits = compressZeroAndOneBits(odd_processed_bit_strings, difference_number_of_bits, even_processed_bit_strings);
                            if(and_number_of_bits > 0)
                            {
                                System.out.println("Data compressed with and.");
                                delta_amount_of_compression += and_number_of_bits / 8;
                            }
                            else
                            {
                                System.out.println("Data did not compress with and.");
                                delta_amount_of_compression += difference_number_of_bits / 8;
                            }
                        }
                        System.out.println("Delta string is " + delta_amount_of_compression + " bytes long.");
                        GetPixelsFromDeltas(delta, shrink, even_xdim / 2, even_ydim / 2, difference_shift, difference_maximum_delta);
                        expandInterp(shrink, even_xdim / 2, even_ydim / 2, difference);
                        for(i = 0; i < even_xdim * even_ydim; i++)
                            green[i] -= difference[i];
                    }
                }
                for(i = 0; i < even_xdim * even_ydim; i++)
                {
                    if(green[i] > 255)
                        green[i] = 255;
                    if(green[i] < 0)
                        green[i] = 0;
                }
                for(i = 0; i < even_xdim * even_ydim; i++)
                {
                    red[i]   = (original_pixel[i] >> 16) & 0xff;
                    blue[i]  = original_pixel[i] & 0xff;        
                    red[i]  = red[i] - green[i];
                    blue[i] = blue[i] - green[i];
                }
    
                maximum_delta = 256;
                maximum_delta >>= pixel_shift;
                number_of_different_values = 2 * maximum_delta + 1;
                shrinkAvg(red, even_xdim, even_ydim, shrink);
                if(pixel_shift > 3)
                {
                getDeltasFromPixels(shrink, delta, even_xdim / 2, even_ydim / 2, pixel_shift, maximum_delta); 
                GetPixelsFromDeltas(delta, difference, even_xdim / 2, even_ydim / 2, pixel_shift, maximum_delta);
                expandInterp(difference, even_xdim / 2, even_ydim / 2, delta);
                for(i = 0; i < even_xdim * even_ydim; i++)
                    delta[i] -= red[i];
                shrinkAvg(delta, even_xdim, even_ydim, difference);
                for(i = 0; i < (even_xdim / 2) * (even_ydim / 2); i++)
                    shrink[i] -= difference[i];
                }
                getDeltasFromPixels(shrink, delta, even_xdim / 2, even_ydim / 2, pixel_shift, maximum_delta);
                number_of_different_values = 2 * maximum_delta + 1;
                number_of_bits = packStrings(delta, (even_xdim / 2) * (even_ydim / 2), number_of_different_values, odd_processed_bit_strings);
                size = (even_xdim / 2) * (even_ydim / 2);
                zero_one_ratio = (double) size / (double)number_of_bits;
                size = (int)(zero_one_ratio * 100.);
                zero_one_ratio = (double)size / 100.;
                System.out.println("The red-green delta string is " + zero_one_ratio + " zeros.");
                original_size = number_of_bits;
                number_of_bits = compressBits(odd_processed_bit_strings, number_of_bits, zero_one_ratio, even_processed_bit_strings);
                if(number_of_bits != -1)
                {
                    delta_amount_of_compression += number_of_bits /8;
                    number_of_bits = decompressBits(even_processed_bit_strings, number_of_bits, odd_processed_bit_strings);
                }
                else
                {
                    number_of_bits = original_size;
                    System.out.println("Data did not compress with or.");
                    and_number_of_bits = compressZeroAndOneBits(odd_processed_bit_strings, number_of_bits, even_processed_bit_strings);
                    if(and_number_of_bits > 0)
                    {
                        System.out.println("Data compressed with and.");
                        delta_amount_of_compression += and_number_of_bits / 8;
                    }
                    else
                    {
                        System.out.println("Data did not compress with and.");
                        delta_amount_of_compression += number_of_bits / 8;
                    }
                }
                System.out.println("Delta string is " + delta_amount_of_compression + " bytes long.");
                number_of_bytes = unpackStrings(odd_processed_bit_strings, number_of_different_values, delta, (even_xdim / 2) * (even_ydim / 2));
                GetPixelsFromDeltas(delta, shrink, even_xdim / 2, even_ydim / 2, pixel_shift, maximum_delta);
                expandInterp(shrink, even_xdim / 2, even_ydim / 2, red);
                for(i = 0; i < even_xdim * even_ydim; i++)
                {
                    red[i] = red[i] + green[i];
                    if(red[i] < 0)
                        red[i] = 0;
                    else if(red[i] > 255)
                        red[i] = 255;
                }
    
                shrinkAvg(blue, even_xdim, even_ydim, shrink);
                if(pixel_shift > 4)
                {
                getDeltasFromPixels(shrink, delta, even_xdim / 2, even_ydim / 2, pixel_shift, maximum_delta); 
                GetPixelsFromDeltas(delta, difference, even_xdim / 2, even_ydim / 2, pixel_shift, maximum_delta);
                expandInterp(difference, even_xdim / 2, even_ydim / 2, delta);
                for(i = 0; i < even_xdim * even_ydim; i++)
                    delta[i] -= blue[i];
                shrinkAvg(delta, even_xdim, even_ydim, difference);
                for(i = 0; i < (even_xdim / 2) * (even_ydim / 2); i++)
                    shrink[i] -= difference[i];
                }
                getDeltasFromPixels(shrink, delta, even_xdim / 2, even_ydim / 2, pixel_shift, maximum_delta);
                number_of_different_values = 2 * maximum_delta + 1;
                number_of_bits = packStrings(delta, (even_xdim / 2) * (even_ydim / 2), number_of_different_values, odd_processed_bit_strings);
                size = (even_xdim / 2) * (even_ydim / 2);
                zero_one_ratio = (double) size / (double)number_of_bits;
                size = (int)(zero_one_ratio * 100.);
                zero_one_ratio = (double)size / 100.;
                System.out.println("The blue-green delta string is " + zero_one_ratio + " zeros.");
                original_size = number_of_bits;
                number_of_bits = compressBits(odd_processed_bit_strings, number_of_bits, zero_one_ratio, even_processed_bit_strings);
                if(number_of_bits != -1)
                {
                    delta_amount_of_compression += number_of_bits / 8;
                    number_of_bits = decompressBits(even_processed_bit_strings, number_of_bits, odd_processed_bit_strings);
                }
                else
                {
                    number_of_bits = original_size;
                    System.out.println("Data did not compress with or.");
                    and_number_of_bits = compressZeroAndOneBits(odd_processed_bit_strings, number_of_bits, even_processed_bit_strings);
                    if(and_number_of_bits > 0)
                    {
                        System.out.println("Data compressed with and.");
                        delta_amount_of_compression += and_number_of_bits / 8;
                    }
                    else
                    {
                        System.out.println("Data did not compress with and.");
                        delta_amount_of_compression += number_of_bits / 8;
                    }
                }
                System.out.println("Delta string is " + delta_amount_of_compression + " bytes long.");
                number_of_bytes = unpackStrings(odd_processed_bit_strings, number_of_different_values, delta, (xdim / 2) * (ydim / 2));
                GetPixelsFromDeltas(delta, shrink, even_xdim / 2, even_ydim / 2, pixel_shift, maximum_delta); 
                expandInterp(shrink, even_xdim / 2, even_ydim / 2, blue);
                for(i = 0; i < even_xdim * even_ydim; i++)
                {
                    blue[i] = blue[i] + green[i];
                    if(blue[i] < 0)
                        blue[i] = 0;
                    else if(blue[i] > 255)
                        blue[i] = 255;
                }

                for(i = 0; i < even_xdim * even_ydim; i++)
                {
                    pixel[i] = 0;
                    pixel[i] |= 255 << 24;
                    pixel[i] |= red[i] << 16;
                    pixel[i] |= green[i] << 8;
                    pixel[i] |= blue[i];
                }
                size = even_xdim * even_ydim;

                delta_amount_of_compression = (1. - (delta_amount_of_compression / (3 * size)));
                delta_amount_of_compression = (double)((int)(1000000. * delta_amount_of_compression));
                delta_amount_of_compression /= 1000000.;

                System.out.println("");
                System.out.println("Amount of compression is " + delta_amount_of_compression);
                System.out.println("");

            }
            image = createImage(new MemoryImageSource(even_xdim, even_ydim, color_model, pixel, 0, even_xdim));
            image_canvas.PutImage(image, image_canvas.getGraphics());
            image_canvas.getGraphics().drawImage(image, 0, 0, this_display);
            user_information.setText("Delta encoded image...compression is " + delta_amount_of_compression + ". \nP = " + pixel_shift + ", E = " + current_delta_quantization_amount + ", S = " + error_amount + ".");
        }
    }

    class ReloadButtonHandler implements ActionListener
    {
        Display this_display;
    
        ReloadButtonHandler(Display display)
        {
            this_display = display;
        }

        public void actionPerformed(ActionEvent e)
        {
            image = createImage(new MemoryImageSource(even_xdim, even_ydim, color_model, original_pixel, 0, even_xdim));
            image_canvas.PutImage(image, image_canvas.getGraphics());
            image_canvas.getGraphics().drawImage(image, 0, 0, this_display);
            user_information.setText("Original image...compression is " + original_amount_of_compression);
        }
    }

    class ErrorScrollbarHandler implements AdjustmentListener
    {
        int      value;
        
        public void adjustmentValueChanged(AdjustmentEvent event)
        {
            value        = event.getValue();
            error_amount = value;
            current_error.setText(" " + Integer.toString(value));
            CurrentResolution = -PreviousResolution;
        }
        
        public void adjustmentValueReset(int new_value)
        {
            current_error.setText(" " + Integer.toString(new_value));
        }
    }


    class PixelDepthScrollbarHandler implements AdjustmentListener
    {
        int      value;
        
        public void adjustmentValueChanged(AdjustmentEvent event)
        {
            value        = event.getValue();
            pixel_depth_quantize_amount = value;
            current_pixel_depth.setText(" " + Integer.toString(value));
            CurrentResolution = -PreviousResolution;
        }
        
        public void adjustmentValueReset(int new_value)
        {
            current_pixel_depth.setText(" " + Integer.toString(new_value));
        }
    }

    class DeltaScrollbarHandler implements AdjustmentListener
    {
        int      value;
        
        public void adjustmentValueChanged(AdjustmentEvent event)
        {
            value        = event.getValue();
            current_delta_quantization_amount = value;
            current_delta_quantization.setText(" " + Integer.toString(value));
            CurrentResolution = -PreviousResolution;
        }
        
        public void adjustmentValueReset(int new_value)
        {
            current_delta_quantization.setText(" " + Integer.toString(new_value));
        }
    }


    public void getDeltasFromPixels(int pixel[], int delta[], int xdim, int ydim, int shift, int maximum_delta)
    {
        int i, j, k;
        int current_value;
        int start_value;
        int delta_value;
    
        k = 0;
        start_value = 0;
        for(i = 0; i < ydim; i++)
        {
            delta_value  = (pixel[k] >> shift) - (start_value >> shift);
            if(delta_value > maximum_delta)
                delta_value = maximum_delta;
            else if(delta_value < -maximum_delta)
                delta_value = -maximum_delta;
            start_value += delta_value << shift;
            delta[k]     = delta_value + maximum_delta;
            k++;
            current_value = start_value; 
            for(j = 1; j < xdim; j++)
            {
                delta_value    = (pixel[k] >> shift) - (current_value >> shift);
                if(delta_value > maximum_delta)
                    delta_value = maximum_delta;
                else if(delta_value < -maximum_delta)
                    delta_value = -maximum_delta;
                current_value += (delta_value << shift);
                delta[k]       = delta_value + maximum_delta;
                k++;
            }
        }
    }

    public void GetPixelsFromDeltas(int delta[], int pixel[], int xdim, int ydim, int shift, int maximum_delta)
    {
        int current_value;
        int start_value;
        int i, j, k;
    
        k = 0;
        start_value = 0;
        for(i = 0; i < ydim; i++)
        {
            start_value  += (delta[k] - maximum_delta) << shift;
            current_value = start_value;
            pixel[k] = current_value;
            k++;
            for(j = 1; j < xdim; j++)
            {
                current_value += (delta[k] - maximum_delta) << shift;
                pixel[k]       = current_value;
                k++;
            }
        }
    }
    
    public int packStrings(int src[], int size, int number_of_different_values, byte dst[])
    {
        int i, j, k;
        int current_byte;
        byte current_bit, value; 
        byte next_bit; 
        int  index, second_index;
        int  number_of_bits;
        int inverse_table[], table[], mask[];
    
        inverse_table = new int[number_of_different_values]; 
        inverse_table[0] = number_of_different_values / 2;
        i = j = number_of_different_values / 2;
        k = 1;
        j++;
        i--;
        while(i >= 0)
        {
            inverse_table[k] = j;
            k++;
            j++;
            inverse_table[k] = i;
            k++;
            i--;
        }

        table = new int[number_of_different_values];
        for(i = 0; i < number_of_different_values; i++)
        {
            j        = inverse_table[i];
            table[j] = i;
        }
    
        mask  = new int[8];
        mask[0] = 1;
        for(i = 1; i < 8; i++)
        {
            mask[i] = mask[i - 1];
            mask[i] *= 2;
            mask[i]++;
        }
    
    
        current_bit = 0;
        current_byte = 0;
        dst[current_byte] = 0;
        for(i = 0; i < size; i++)
        {
            k     = src[i];
            index = table[k];
            if(index == 0)
            {
                current_bit++;
                if(current_bit == 8)
                    dst[++current_byte] = current_bit = 0;
            }
            else
            {
                next_bit = (byte)((current_bit + index + 1) % 8);
                if(index <= 7)
                {
                    dst[current_byte] |= (byte) (mask[index - 1] << current_bit);
                    if(next_bit <= current_bit)
                    {
                        dst[++current_byte] = 0;
                        if(next_bit != 0)
                            dst[current_byte] |= (byte)(mask[index - 1] >> (8 - current_bit));
                    }
                }
                else if(index > 7)
                {
                    dst[current_byte] |= (byte)(mask[7] << current_bit);
                    j = (index - 8) / 8;
                    for(k = 0; k < j; k++)
                        dst[++current_byte] = (byte)(mask[7]);
                    dst[++current_byte] = 0;
                    if(current_bit != 0)
                        dst[current_byte] |= (byte)(mask[7] >> (8 - current_bit));
    
                    if(index % 8 != 0)
                    {
                        second_index = index % 8 - 1;
                        dst[current_byte] |= (byte)(mask[second_index] << current_bit);
                        if(next_bit <= current_bit)
                        {
                            dst[++current_byte] = 0;
                            if(next_bit != 0)
                                dst[current_byte] |= (byte)(mask[second_index] >> (8 - current_bit));
                        }
                    }
                    else if(next_bit <= current_bit)
                            dst[++current_byte] = 0;
                }
                current_bit = next_bit;
            }
        }
        if(current_bit != 0)
            current_byte++;
        number_of_bits = current_byte * 8;
        if(current_bit != 0)
            number_of_bits -= 8 - current_bit;
        return(number_of_bits);
    }
    
    public int unpackStrings(byte src[], int number_of_different_values, int dst[], int size)
    {
        int  number_of_bytes_unpacked, i;
        int  current_src_byte, current_dst_byte;
        byte non_zero, mask, current_bit;
        int  index, current_length;
        int table[];
    
        table = new int[number_of_different_values]; 
        table[0] = number_of_different_values / 2;
        i = j = number_of_different_values / 2;
        k = 1;
        j++;
        i--;
        while(i >= 0)
        {
            table[k] = j;
            k++;
            j++;
            table[k] = i;
            k++;
            i--;
        }
        current_length = 1;
        current_src_byte = 0;
        mask = 0x01;
        current_bit = 0;
        current_dst_byte = 0;
        while(current_dst_byte < size)
        {
            non_zero = (byte)(src[current_src_byte] & (byte)(mask << current_bit));
            if(non_zero != 0)
                current_length++;
            else
            {
                index = current_length - 1;
                dst[current_dst_byte++] = table[index];
                current_length = 1;
            }
            current_bit++;
            if(current_bit == 8)
            {
                current_bit = 0;
                current_src_byte++;
            }
        }
        if(current_bit == 0)
            number_of_bytes_unpacked = current_src_byte;
        else
            number_of_bytes_unpacked = current_src_byte + 1;
        return(number_of_bytes_unpacked);
    }

    public int compressZeroAndOneBits(byte src[], int size, byte dst[])
    {
        byte mask, current_bit;
        int  current_byte;
        int  parser_bit_type;
        int  i, j, k;
        int  minimum_amount_of_compression, byte_size;

        byte_size = size / 8;
        minimum_amount_of_compression = 0;
        j = k = 0;
        current_byte = 0;
        current_bit = 0;
        mask = 0x01;
        dst[0] = 0;
        parser_bit_type = 0; 
        for(i = 0; i < size; i++)
        {
            if((current_byte + (byte_size - i / 8) / 2 + minimum_amount_of_compression) > byte_size) 
                return(-1);

            if(parser_bit_type == 0)
            {
                if((src[k] & (mask << j)) == 0)
                {
                    i++;
                    j++;
                    if(j == 8)
                    {
                        j = 0;
                        k++;
                    }
                    if((src[k] & (mask << j)) == 0)
                    {
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                    }
                    else
                    {
                        dst[current_byte] |= (byte)mask << current_bit;
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                        parser_bit_type = 1;
                    }
                }
                else
                {
                    dst[current_byte] |= (byte)mask << current_bit;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                    dst[current_byte] |= (byte)mask << current_bit;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                    parser_bit_type = 1;
                }
            }
            else // parser_bit_type == 1
            {
                if((src[k] & (mask << j)) != 0)
                {
                    i++;
                    j++;
                    if(j == 8)
                    {
                        j = 0;
                        k++;
                    }
                    if((src[k] & (mask << j)) != 0)
                    {
                        dst[current_byte] |= (byte)mask << current_bit;
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                    }
                    else
                    {
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                        dst[current_byte] |= (byte)mask << current_bit;
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                        parser_bit_type = 0;
                    }
                }
                else
                {
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                    parser_bit_type = 0;
                }
            }
            j++;
            if(j == 8)
            {
                j = 0;
                k++;
            }
        }
        if(current_bit != 0)
            current_byte++;
        number_of_bits = current_byte * 8;
        if(current_bit != 0)
            number_of_bits -= 8 - current_bit;
        return(number_of_bits);
    }

    public int decompressZeroAndOneBits(byte src[], int size, byte dst[])
    {
        byte mask, current_bit;
        int  current_byte;
        int  parser_bit_type;
        int  number_of_bits;
        int  i, j, k;

        current_byte = 0;
        current_bit  = 0;
        parser_bit_type = 0;
        dst[0] = 0;
        mask = 0x01;
        j = k = 0;
        for(i = 0; i < size; i++)
        {
            if(parser_bit_type == 0)
            {
                if((src[k] & (mask << j)) != 0)
                {
                    parser_bit_type = 1;
                    i++;
                    j++;
                    if(j == 8)
                    {
                        j = 0;
                        k++;
                    }
                    if((src[k] & (mask << j)) == 0)
                    {
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                        dst[current_byte] |= (byte)mask << current_bit;
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                    }
                    else
                    {
                        dst[current_byte] |= (byte)mask << current_bit;
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                    }
                }
                else
                {
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = 0;
                        dst[current_byte] = 0;
                    }
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = 0;
                        dst[current_byte] = 0;
                    }
                }
            }
            else // parser_bit_type == 1
            {
                if((src[k] & (mask << j)) == 0)
                {
                    parser_bit_type = 0;
                    i++;
                    j++;
                    if(j == 8)
                    {
                        j = 0;
                        k++;
                    }
                    if((src[k] & (mask << j)) == 0)
                    {
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                    }
                    else
                    {
                        dst[current_byte] |= (byte)mask << current_bit;
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                    }
                }
                else
                {
                    dst[current_byte] |= (byte)mask << current_bit;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = 0;
                        dst[current_byte] = 0;
                    }
                    dst[current_byte] |= (byte)mask << current_bit;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = 0;
                        dst[current_byte] = 0;
                    }
                }
            }
            j++;
            if(j == 8)
            {
                j = 0;
                k++;
            }
        }
        if(current_bit != 0)
            current_byte++;
        number_of_bits = current_byte * 8;
        if(current_bit != 0)
            number_of_bits -= 8 - current_bit;
        return(number_of_bits);
    }

    public int compressZeroOrOneBits(byte src[], int size, byte dst[], int bit_type, double zero_one_ratio[])
    {
        byte mask, current_bit;
        int  current_byte;
        int  number_of_zero_bits;
        int  number_of_bits;
        int  current_length;
        int  i, j, k;
        int  minimum_amount_of_compression, byte_size;

        byte_size = size / 8;
        minimum_amount_of_compression = 0;
        j = k = number_of_zero_bits = 0;
        current_byte = 0;
        current_bit = 0;
        mask = 0x01;
        dst[0] = 0;

        if(bit_type == 0)
        {
            for(i = 0; i < size; i++)
            {
                if((current_byte + (byte_size - i / 8) / 2 + minimum_amount_of_compression) > byte_size) 
                    return(-1);
                if((src[k] & (mask << j)) == 0)
                {
                    i++;
                    j++;
                    if(j == 8)
                    {
                        j = 0;
                        k++;
                    }
                    if((src[k] & (mask << j)) == 0)
                    {
                        number_of_zero_bits++;
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                    }
                    else
                    {
                        dst[current_byte] |= (byte)mask << current_bit;
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                        dst[current_byte] |= (byte)mask << current_bit;
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                    }
                }
                else
                {
                    dst[current_byte] |= (byte)mask << current_bit;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                    number_of_zero_bits++;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                }
                j++;
                if(j == 8)
                {
                    j = 0;
                    k++;
                }
            }
        }
        else
        {
            for(i = 0; i < size; i++)
            {
                if((current_byte + (byte_size - i / 8) / 2 + minimum_amount_of_compression) > byte_size) 
                    return(-1);
                if((src[k] & (mask << j)) != 0)
                {
                    i++;
                    j++;
                    if(j == 8)
                    {
                        j = 0;
                        k++;
                    }
                    if((src[k] & (mask << j)) != 0)
                    {
                        dst[current_byte] |= (byte)mask << current_bit;
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                    }
                    else
                    {
                        number_of_zero_bits++;
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                        number_of_zero_bits++;
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                    }
                }
                else
                {
                    number_of_zero_bits++;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                    dst[current_byte] |= (byte)mask << current_bit;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                }
                j++;
                if(j == 8)
                {
                    j = 0;
                    k++;
                }
            }
        }
        if(current_bit != 0)
            current_byte++;
        number_of_bits = current_byte * 8;
        if(current_bit != 0)
            number_of_bits -= 8 - current_bit;
        zero_one_ratio[0] = (double)number_of_zero_bits;
        zero_one_ratio[0] /= (double)number_of_bits;
        return(number_of_bits);
    }

    public int decompressZeroOrOneBits(byte src[], int size, byte dst[], int bit_type)
    {
        byte mask, current_bit;
        int  current_byte;
        int  current_length;
        int  number_of_bits;
        int  i, j, k;

        current_length = 0;
        current_byte = 0;
        current_bit  = 0;
        dst[0] = 0;
        mask = 0x01;
        j = k = 0;
        if(bit_type == 0)
        {
            for(i = 0; i < size; i++)
            {
                if((src[k] & (mask << j)) != 0)
                {
                    i++;
                    j++;
                    if(j == 8)
                    {
                        j = 0;
                        k++;
                    }
                    if((src[k] & (mask << j)) != 0)
                    {
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                        dst[current_byte] |= (byte)mask << current_bit;
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                    }
                    else
                    {
                        dst[current_byte] |= (byte)mask << current_bit;
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                    }
                }
                else
                {
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                }
                j++;
                if(j == 8)
                {
                    j = 0;
                    k++;
                }
            }
        }
        else
        {
            for(i = 0; i < size; i++)
            {
                if((src[k] & (mask << j)) == 0)
                {
                    i++;
                    j++;
                    if(j == 8)
                    {
                        j = 0;
                        k++;
                    }
                    if((src[k] & (mask << j)) == 0)
                    {
                        dst[current_byte] |= (byte)mask << current_bit;
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                    }
                    else
                    {
                        current_bit++;
                        if(current_bit == 8)
                        {
                            current_byte++;
                            current_bit = dst[current_byte] = 0;
                        }
                        current_length = 0;
                    }
                }
                else
                {
                    dst[current_byte] |= (byte)mask << current_bit;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                    dst[current_byte] |= (byte)mask << current_bit;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                }
                j++;
                if(j == 8)
                {
                    j = 0;
                    k++;
                }
            }
        }
        if(current_bit != 0)
            current_byte++;
        number_of_bits = current_byte * 8;
        if(current_bit != 0)
            number_of_bits -= 8 - current_bit;
        return(number_of_bits);
    }

    public int compressBits(byte src[], int size, double zero_one_ratio, byte dst[])
    {
        int number_of_iterations, current_size, previous_size;
        int byte_size, type, i;
        byte mask;
        double new_zero_one_ratio[];
        
        if(zero_one_ratio > .4 && zero_one_ratio < .6)
            return(-1);
        else
        {
            if(zero_one_ratio <= .4)
                type = 1;
            else
                type = 0;
            byte_size = size / 8;
            if(size % 8 != 0)
                byte_size++;
            new_zero_one_ratio = new double[1];
            new_zero_one_ratio[0] = zero_one_ratio;
            number_of_iterations = 1;
            current_size = previous_size = size;
            current_size = compressZeroOrOneBits(src, previous_size, dst, type, new_zero_one_ratio);
            while(current_size < previous_size && current_size > 1)
            {
                previous_size = current_size;
                if(number_of_iterations % 2 == 1)
                    current_size = compressZeroOrOneBits(dst, previous_size, temp, type, new_zero_one_ratio);
                else
                    current_size = compressZeroOrOneBits(temp, previous_size, dst, type, new_zero_one_ratio);
                number_of_iterations++;
            }
            if(current_size == -1 && number_of_iterations > 1)
            {
                current_size = previous_size;
                number_of_iterations--;
            }
            if(number_of_iterations % 2 == 0)
            {
                byte_size = current_size / 8;
                if(current_size % 8 != 0)
                    byte_size++; 
                for(i = 0; i < byte_size; i++)
                    dst[i] = temp[i];
            }    
        }
        if(current_size > 0)
        {
            // System.out.println("The number of iterations was " + number_of_iterations);
            if(current_size % 8 == 0)
            {
                byte_size = current_size / 8;
                dst[byte_size] = (byte) number_of_iterations;
                if(type == 1)
                    dst[byte_size] |= 128;
                else
                    dst[byte_size] &= 127;
            }
            else
            {
                int  remainder = current_size % 8;
                byte_size = current_size / 8;

                dst[byte_size] |= (byte) (number_of_iterations << remainder);
                byte_size++;
                dst[byte_size] = 0;
                if(remainder > 1)
                    dst[byte_size] = (byte) (number_of_iterations >> 8 - remainder);
                if(type == 1)
                {
                    mask = (byte)1;
                    mask <<= remainder - 1; 
                    dst[byte_size] |= mask;
                }
            }
            return(current_size + 8);
        }
        else
            return(-1);
    }
    
    public int decompressBits(byte src[], int size, byte dst[])
    {
        int  byte_index;
        int  type, number_of_iterations;
        int  addend;
        int  mask;
        int  remainder, i, even, odd;
        int  previous_size, current_size, byte_size;
        double new_zero_one_ratio[] = new double[1];
        
        byte_index = size / 8 - 1;
        remainder  = size % 8;
        if(remainder != 0)
        {
            int value = 254;
            addend = 2;
            for(i = 1; i < remainder; i++)
            {
                value -= addend;
                addend <<= 1;
            } 
            mask = value;
            number_of_iterations = src[byte_index];
            if(number_of_iterations < 0)
                number_of_iterations += 256;
            number_of_iterations &= mask;
            number_of_iterations >>= remainder;
            byte_index++;
            if(remainder > 1)
            {
                mask = 1;
                for(i = 2; i < remainder; i++)
                {
                    mask <<= 1;
                    mask++;
                }
                addend = src[byte_index]; 
                if(addend < 0)
                    addend += 256;
                addend &= mask;
                addend <<= 8 - remainder;
                number_of_iterations += addend;
                mask++;
            }
            else
                mask = 1;
            if((src[byte_index] & mask) == 0)
                type = 0;
            else
                type = 1;
        }
        else
        {
           mask = 127;
           number_of_iterations = src[byte_index];
           if(number_of_iterations < 0)
               number_of_iterations += 256; 
           number_of_iterations &= mask;
           mask++;
           if((src[byte_index] & mask) == 0)
               type = 0;
           else
               type = 1;
        }
        // System.out.println("The number of iterations was " + number_of_iterations);
        // System.out.println("The type was " + type);
        
        current_size = 0;
        if(number_of_iterations == 1)
        {
            current_size = decompressZeroOrOneBits(src, size - 8, dst, type);
            number_of_iterations--;
        }
        else if(number_of_iterations % 2 == 0)
        {
            current_size = decompressZeroOrOneBits(src, size - 8, temp, type);
            number_of_iterations--;
            while(number_of_iterations > 0)
            {
                previous_size = current_size;
                if(number_of_iterations % 2 == 0)
                    current_size = decompressZeroOrOneBits(dst, previous_size, temp, type);
                else
                    current_size = decompressZeroOrOneBits(temp, previous_size, dst, type);
                number_of_iterations--;
            }
        }
        else
        {
            current_size = decompressZeroOrOneBits(src, size - 8, dst, type);
            number_of_iterations--;
            while(number_of_iterations > 0)
            {
                previous_size = current_size;
                if(number_of_iterations % 2 == 0)
                    current_size = decompressZeroOrOneBits(dst, previous_size, temp, type);
                else
                    current_size = decompressZeroOrOneBits(temp, previous_size, dst, type);
                number_of_iterations--;
            }
        }
        return(current_size);
    }

    public void expandInterp(int src[], int xdim, int ydim, int dst[])
    {
        int       i, j, x, y, new_xdim;
        new_xdim  = 2 * xdim;
        i         = 0; 
        j         = 0;

        // Process the top row.
        dst[j]                = src[i];
        dst[j + 1]            = (1333 * src[i] + 894 * src[i + 1]) / 2227;
        dst[j + new_xdim]     = (1333 * src[i] + 894 * src[i + xdim]) / 2227;
        dst[j + new_xdim + 1] = (1000  * src[i] + 447 * src[i + 1] + 447 * src[i + xdim] + 333 * src[i + xdim + 1]) / 2227;
        i++; 
        j                    += 2;
        for(x = 1; x < xdim - 1; x++)
        {
            dst[j]                = (1333 * src[i] + 894 * src[i - 1]) / 2227; 
            dst[j + 1]            = (1333 * src[i] + 894 * src[i + 1]) / 2227; 
            dst[j + new_xdim]     = (1000 * src[i] + 447 * src[i - 1] + 447 * src[i + xdim] + 333 * src[i + xdim - 1]) / 2227; 
            dst[j + new_xdim + 1] = (1000  * src[i] + 447 * src[i + 1] + 447 * src[i + xdim] + 333 * src[i + xdim + 1]) / 2227; 
            j += 2;
            i++;
        }
        dst[j]                = (1333 * src[i] + 894 * src[i - 1]) / 2227;
        dst[j + 1]            = src[i];
        dst[j + new_xdim]     = (1000  * src[i] + 447 * src[i - 1] + 447 * src[i + xdim] + 333 * src[i + xdim - 1]) / 2227;
        dst[j + new_xdim + 1] = (1333 * src[i] + 894 * src[i + xdim]) / 2227;
        i++; 
        j                    += new_xdim + 2;

        // Process the middle section.
        for(y = 1; y < ydim - 1; y++)
        {
            dst[j]                = (1333 * src[i] + 894 * src[i - xdim]) / 2227;
            dst[j + 1]            = (1000  * src[i] + 447 * src[i - xdim] + 447 * src[i + 1] + 333 * src[i - xdim + 1]) / 2227;
            dst[j + new_xdim]     = (1333 * src[i] +  894 * src[i + xdim]) / 2227; 
            dst[j + new_xdim + 1] = (1000  * src[i] + 447 * src[i + 1] + 447 * src[i + xdim] + 333 * src[i + xdim + 1]) / 2227;
            i++;
            j += 2;
            for(x = 1; x < xdim - 1; x++)
            {
                dst[j]                = (1000  * src[i] + 447 * src[i - xdim] + 447 * src[i - 1] + 333 * src[i - xdim - 1]) / 2227;
                dst[j + 1]            = (1000 * src[i] + 447 * src[i - xdim] + 447 * src[i + 1] + 333 * src[i - xdim + 1]) / 2227;
                dst[j + new_xdim]     = (1000 * src[i] + 447 * src[i + xdim] + 447 * src[i - 1] + 333 * src[i + xdim - 1]) / 2227; 
                dst[j + new_xdim + 1] = (1000 * src[i] + 447 * src[i + 1] + 447 * src[i + xdim] + 333 * src[i + xdim + 1]) / 2227;
                i++;
                j += 2;
            }
            dst[j]                = (1000  * src[i] + 894 * src[i - xdim] + 447 * src[i - 1]) / 2227;
            dst[j + 1]            = (1333 * src[i] + 894 * src[i - xdim]) / 2227;
            dst[j + new_xdim]     = (1000  * src[i] + 447 * src[i - 1] + 447 * src[i + xdim] + 333 * src[i + xdim - 1]) / 2227;
            dst[j + new_xdim + 1] = (1333 * src[i] + 894 * src[i + xdim]) / 2227; 
            i++;
            j += new_xdim + 2;
        }

        // Process the bottom row.
        dst[j]                = (1333 * src[i] + 894 * src[i - xdim]) / 2227;
        dst[j + 1]            = (1000  * src[i] + 447 * src[i + 1] + 447 * src[i - xdim] + 333 * src[i - xdim + 1]) / 2227;
        dst[j + new_xdim]     = src[i];
        dst[j + new_xdim + 1] = (1333 * src[i] + 894 * src[i + 1]) / 2227;
        i++;
        j                    += 2;
        for(x = 1; x < xdim - 1; x++)
        {
            dst[j]                = (1000  * src[i] + 447 * src[i - 1] + 447 * src[i - xdim] + 333 * src[i - xdim - 1]) / 2227; 
            dst[j + 1]            = (1000  * src[i] + 447 * src[i + 1] + 447 * src[i - xdim] + 333 * src[i - xdim + 1]) / 2227; 
            dst[j + new_xdim]     = (1333 * src[i] + 894 * src[i - 1]) / 2227; 
            dst[j + new_xdim + 1] = (1333 * src[i] + 894 * src[i + 1]) / 2227; 
            i++;
            j += 2;
        }
        dst[j]                = (1000  * src[i] + 447 * src[i - 1] + 447 * src[i - xdim] + 333 * src[i - xdim - 1]) / 2227;
        dst[j + 1]            = (1333 * src[i] + 894 * src[i - xdim]) / 2227;
        dst[j + new_xdim]     = (1333 * src[i] + 894 * src[i - 1]) / 2227;
        dst[j + new_xdim + 1] = src[i];
    }

    public void shrinkAvg(int src[], int xdim, int ydim, int dst[])
    {
        int    i, j, k, r, c;
        int    sum;
    
        r = ydim;
        c = xdim;
        k = 0;
        if(xdim % 2 == 0)
        {
            for(i = 0; i < r - 1; i += 2)
            {
                for (j = 0; j < c - 1; j += 2)
                {
                    sum = (25 * src[i*c+j] + 25 * src[i*c+ j+1] + 25 * src[(i+1)*c+j] + 25 * src[(i+1)*c+j+1]) / 100;
                    dst[k++] = sum;
                }
            }
        }
        else
        {
            for(i = 0; i < r - 1; i += 2)
            {
                for(j = 0; j < c - 1; j += 2)
                {
                    sum= (25 * src[i*c+j] + 25 * src[i*c+ j+1] + 25 * src[(i+1)*c+j] + 25 * src[(i+1)*c+j+1]) / 100;
                    dst[k++] = sum;
                }
                k++;
            }
        }
    }
    
    public void extract(int src[], int xdim, int ydim, int xoffset, int yoffset, int dst[], int  new_xdim, int new_ydim)
    {
        int x, y, i, j, xend, yend;
    
        xend = xoffset + new_xdim;
        yend = yoffset + new_ydim;
        j    = 0;
        for(y = yoffset; y < yend; y++)
        {
            i = y * xdim + xoffset;
            for(x = xoffset; x < xend; x++)
            {
                dst[j] = src[i];
                j++;
                i++;
            }
        }
    }

    public void avgAreaXTransform(int src[], int xdim, int ydim, int dst[], int new_xdim, int start_fraction[], int end_fraction[], int number_of_pixels[])
    {
        int    i, j, k, x, y;
        int    weight, current_whole_number, previous_whole_number;
        int    total, factor;
        double real_position, differential, previous_position;
    
        differential = (double)xdim / (double)new_xdim;
        weight       = (int)(differential * xdim);
        weight       *= 1000;
        factor       = 1000 * xdim;

        real_position = 0.;
        current_whole_number = 0;
        for(i = 0; i < new_xdim; i++)
        {
            previous_position     = real_position;
            previous_whole_number = current_whole_number;
            real_position        += differential;
            current_whole_number  = (int)(real_position);
            number_of_pixels[i]   = current_whole_number - previous_whole_number;
            start_fraction[i]     = (int)(1000. * (1. - (previous_position - (double)(previous_whole_number)))); 
            end_fraction[i]       = (int)(1000. * (real_position - (double)(current_whole_number)));
        }

        for(y = 0; y < ydim; y++)
        {
            i = y * new_xdim;
            j = y * xdim;
            for(x = 0; x < new_xdim - 1; x++)
            {
                if(number_of_pixels[x] == 0)
                {
                    dst[i] = src[j];
                    i++;
                }
                else
                {
                    total = start_fraction[x] * xdim * src[j];
                    j++;
                    k = number_of_pixels[x] - 1;
                    while(k > 0)
                    {
                        total += factor * src[j];
                        j++;
                        k--;
                    }
                    total += end_fraction[x] * xdim * src[j];
                    total /= weight;
                    dst[i] = total;
                    i++;
                }
            }
            if(number_of_pixels[x] == 0)
                dst[i] = src[j];
            else
            {
                total = start_fraction[x] * xdim * src[j];
                j++;
                k = number_of_pixels[x] - 1;
                while(k > 0)
                {
                    total += factor * src[j];
                    j++;
                    k--;
                }
                total /= weight - end_fraction[x] * xdim;
                dst[i] = total;
            }
        }
    }

    public void avgAreaYTransform(int src[], int xdim, int ydim, int dst[], int new_ydim, int start_fraction[], int end_fraction[], int number_of_pixels[])
    {
        int    i, j, x, y;
        int    weight, current_whole_number, previous_whole_number;
        int    total, factor;
        double real_position, differential, previous_position;
    
        differential = (double)ydim / (double)new_ydim;
        weight       = (int)(differential * ydim);
        weight       *= 1000;
        factor       = ydim * 1000;
        
        real_position = 0.;
        current_whole_number = 0;
        for(i = 0; i < new_ydim; i++)
        {
            previous_position     = real_position;
            previous_whole_number = current_whole_number;
            real_position        += differential;
            current_whole_number  = (int)(real_position);
            number_of_pixels[i]   = current_whole_number - previous_whole_number;
            start_fraction[i]     = (int) (1000. * (1. - (previous_position - (double)(previous_whole_number)))); 
            end_fraction[i]       = (int) (1000. * (real_position - (double)(current_whole_number)));
        }

        for(x = 0; x < xdim; x++)
        {
            i = j = x;
            for(y = 0; y < new_ydim - 1; y++)
            {
                if(number_of_pixels[y] == 0)
                {
                    dst[i] = src[j];
                    i += xdim;
                }
                else
                {
                    total    = start_fraction[y] * ydim * src[j];
                    j       += xdim;
                    k        = number_of_pixels[y] - 1;
                    while(k > 0)
                    {
                        total += factor * src[j];
                        j += xdim;
                        k--;
                    }
                    total   += end_fraction[y] * ydim * src[j];
                    total   /= weight;
                    dst[i]   = total;
                    i       += xdim;
                }
            }
            if(number_of_pixels[y] == 0)
                dst[i] = src[j];
            else
            {
                total    = start_fraction[y] * ydim * src[j];
                j       += xdim;
                k        = number_of_pixels[y] - 1;
                while(k > 0)
                {
                    total += factor * src[j];
                    j += xdim;
                    k--;
                }
                total /= weight - end_fraction[y] * ydim;
                dst[i]   = total;
            }
        }
    }

    public void avgAreaTransform(int src[], int xdim, int ydim, int dst[], int new_xdim, int new_ydim, int workspace[], int start_fraction[], int end_fraction[], int number_of_pixels[])
    {
        avgAreaXTransform(src, xdim, ydim, workspace, new_xdim, start_fraction, end_fraction, number_of_pixels);
        avgAreaYTransform(workspace, new_xdim, ydim, dst, new_ydim, start_fraction, end_fraction, number_of_pixels);
    }
}
