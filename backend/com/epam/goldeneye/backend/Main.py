import HTMLParser
import os.path
from os import walk
import random
import string
import json

import tornado.ioloop
import tornado.web

import face_recognition
import re
from PIL import Image, ImageDraw, ImageFont
import numpy

PORT = 8888
known_names = []
known_face_encodings = []


class PingHandler(tornado.web.RequestHandler):
    def data_received(self, chunk):
        pass

    def get(self):
        print "ping from {}".format(self.get_user_locale())
        self.write(json.dumps({"data": "Hello, world"}))


class RecognizeHandler(tornado.web.RequestHandler):
    def data_received(self, chunk):
        pass

    def post(self):
        result = self.read_upload()
        results = test_image(result)

        serialized_data = json.dumps(results)
        self.render("../web/recognition_results.html", **{'data': serialized_data})
        # self.finish(json.dumps(results))

    def read_upload(self):
        file1 = self.request.files['file1'][0]
        original_fname = file1['filename']
        # extension = os.path.splitext(original_fname)[1]
        # fname = ''.join(random.choice(string.ascii_lowercase + string.digits) for x in range(6))
        # final_filename = fname + extension
        output_file = open("data/uploads/" + original_fname, 'w+')
        output_file.write(file1['body'])

        print("file" + original_fname + " is uploaded")
        return output_file


def generate_imgs(img_dir):
    img_tags = []
    for (_, _, filenames) in walk(img_dir):
        print(filenames)
        for filename in filenames:
            img_tags.append("/images/{}/{}".format(img_dir, filename))
        break
    print(img_tags)
    return img_tags


class IndexHandler(tornado.web.RequestHandler):
    def data_received(self, chunk):
        pass

    def get(self):
        known_faces_imgs = generate_imgs("data/known_faces")
        recent_uploads_imgs = generate_imgs("data/uploads")

        self.render("../web/index.html", known_faces=known_faces_imgs, recent_uploads=recent_uploads_imgs)


class ImageHandler(tornado.web.RequestHandler):
    def data_received(self, chunk):
        pass

    def get(self, url):
        print("give me image {}".format(self.request.path))
        try:
            path = self.get_image_path()
            print("giving from {}".format(path))
            with open(path, 'rb') as f:
                data = f.read()
            self.write(data)
        except Exception as e:
            print('error {}'.format(e))
        finally:
            self.finish()

    def get_image_path(self):
        path_split = self.request.path.split('/')
        if not path_split[len(path_split)-1]:
            path_split = path_split[0:-1]
        path = "data/{}/{}".format(path_split[len(path_split)-2], path_split[len(path_split)-1])
        return path


def scan_known_people(known_people_folder):
    for _file in image_files_in_folder(known_people_folder):
        file_path = os.path.basename(_file)
        print(file_path)
        basename = os.path.splitext(file_path)[0]
        img = face_recognition.load_image_file(_file)
        encodings = face_recognition.face_encodings(img)

        if len(encodings) > 1:
            print("WARNING: More than one face found in {}. Only considering the first face.".format(_file))

        if len(encodings) == 0:
            print("WARNING: No faces found in {}. Ignoring file.".format(_file))
        else:
            known_names.append(basename)
            known_face_encodings.append(encodings[0])
            print("{} found in {}".format(basename, file_path))


def image_files_in_folder(folder):
    return [os.path.join(folder, f) for f in os.listdir(folder) if re.match(r'.*\.(jpg|jpeg|png)', f, re.I)]


def test_image(image_to_check, tolerance=0.6):
    recognized_faces = []

    unknown_image = face_recognition.load_image_file(image_to_check)

    # Scale down image if it's giant so things run a little faster
    unknown_image = scale_image(unknown_image)

    unknown_encodings = face_recognition.face_encodings(unknown_image)
    face_landmarks_list = face_recognition.face_landmarks(unknown_image)
    face_locations = face_recognition.face_locations(unknown_image)

    pil_image = Image.fromarray(unknown_image)
    d = ImageDraw.Draw(pil_image)

    if not unknown_encodings:
        # print out fact that no faces were found in image
        print_result(image_to_check, "no_persons_found", None)

    else:
        for unknown_encoding, face_landmarks, face_location in zip(unknown_encodings, face_landmarks_list,
                                                                   face_locations):
            distances = face_recognition.face_distance(known_face_encodings, unknown_encoding)

            for distance, name in zip(distances, known_names):
                if distance <= tolerance:
                    print_result(image_to_check, name, distance)
                    recognized_faces.append(
                        {'name': name, 'dist': distance, 'landmarks': face_landmarks, 'face_location': face_location}
                    )
                else:
                    print_result(image_to_check, "unknown_person", None)

        for item in recognized_faces:
            face_landmarks = item['landmarks']
            face_location = item['face_location']
            # Print the location of each facial feature in this image
            # Let's trace out each facial feature in the image with a line!
            for facial_feature in face_landmarks.keys():
                print("The {} in this face has the following points: {}".format(facial_feature,
                                                                                face_landmarks[facial_feature]))
                d.line(face_landmarks[facial_feature], width=3)

            # Print the location of each face in this image
            top, right, bottom, left = face_location
            print(
                "A face is located at pixel location Top: {}, Left: {}, Bottom: {}, Right: {}".format(top, left, bottom,
                                                                                                      right))
            d.rectangle(((left, top), (right, bottom)), outline=4)
            font = ImageFont.truetype("font/arial.ttf", size=30)
            title = item['name']
            text_size = d.textsize(title, font)
            d.text((left, bottom - text_size[1]), title, font=font, fill='white')

    pil_image.save("data/recognition_results/result.jpg")

    return recognized_faces


def scale_image(unknown_image):
    if max(unknown_image.shape) > 1000:
        pil_img = Image.fromarray(unknown_image)
        pil_img.thumbnail((1000, 1000), Image.LANCZOS)
        unknown_image = numpy.array(pil_img)
    return unknown_image


def print_result(filename, name, distance):
    print("{},{},{}".format(filename, name, distance))


def make_app():
    return tornado.web.Application([
        (r"/", IndexHandler),
        (r"/ping", PingHandler),
        (r"/recognize", RecognizeHandler),
        (r"/images/(.*)", ImageHandler),
    ])


def main():
    print("starting..")
    scan_known_people("data/known_faces")
    app = make_app()
    app.listen(PORT)
    print("ready!")
    tornado.ioloop.IOLoop.current().start()


if __name__ == "__main__":
    main()
